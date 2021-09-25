package Utils

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic.config
import org.cloudbus.cloudsim.resources.{Bandwidth, Processor, Ram}
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.autoscaling.resources.{ResourceScalingGradual, ResourceScalingInstantaneous}
import org.cloudsimplus.autoscaling.{HorizontalVmScalingSimple, VerticalVmScalingSimple, VmScaling}
import org.cloudsimplus.listeners.VmHostEventInfo

import java.util.function
import java.util.function.{Predicate, Supplier}

/**
 * Enum used to specify the autoscaling policy to adopt
 */
enum ScalingStrategy:
  case HORIZONTAL, VERTICAL, BOTH, NONE

/**
 * Wrapper over the Vm class, used as a support to create completely configurable VMs with autoscaling capabilities, as explained in the documentation.
 */
object VmWithScalingFactory {

  /**
   * Creates a VM with the specific autoscaling requested by the user
   *
   * @param scalingStrategyId the id that can be found in the .conf file
   * @return the VM with the desired autoscaling policy applied
   */
  def apply(scalingStrategyId: Int): Vm = {

    val vm = createVm()
    val scalingStrategy: ScalingStrategy = mapScalingIdToStrategy(scalingStrategyId);

    scalingStrategy match {
      case ScalingStrategy.HORIZONTAL => buildHorizontalScaling(vm)
      case ScalingStrategy.VERTICAL => buildVerticalScaling(vm)
      case ScalingStrategy.BOTH => {
        buildHorizontalScaling(vm)
        buildVerticalScaling(vm)
      }
      case _ => vm
    }
  }

  /**
   * Creates a VM with the specific autoscaling requested by the user. In this case the user can also specify the PEs and MIPS
   * capacity of each VM.
   *
   * @param scalingStrategyId the id that can be found in the .conf file
   * @return the VM with the desired autoscaling policy applied
   */
  def apply(scalingStrategyId: Int, vmPEs: Int, vmMips: Int = config.getInt("host.mipsCapacityPE")): Vm = {

    val vm = createVm(vmPEs, vmMips)
    val scalingStrategy: ScalingStrategy = mapScalingIdToStrategy(scalingStrategyId);

    scalingStrategy match {
      case ScalingStrategy.HORIZONTAL => buildHorizontalScaling(vm)
      case ScalingStrategy.VERTICAL => buildVerticalScaling(vm)
      case ScalingStrategy.BOTH => {
        buildHorizontalScaling(vm)
        buildVerticalScaling(vm)
      }
      case _ => vm
    }
  }

  /**
   * Create a Virtual Machine using the parameters to define its characteristics.
   * It represent a Supplier of VMs, used also by the horizontal scaling policy for this purpose.
   *
   * @param vmPEs the number of PEs for the vm
   * @param vmMips the MIPS capacity of the vm
   * @return the newly created virtual machine
   */
  def createVm(vmPEs: Int = config.getInt("vm.PEs"), vmMips: Int = config.getInt("host.mipsCapacityPE")): Vm = {
    val ramInMBs = config.getInt("vm.RAMInMBs")
    val storageInMBs = config.getLong("vm.StorageInMBs")
    val bwInMBps = config.getInt("vm.BandwidthInMBps")

    new VmSimple(vmMips, vmPEs)
      .setRam(ramInMBs)
      .setBw(bwInMBps)
      .setSize(storageInMBs)
      .setCloudletScheduler(new CloudletSchedulerSpaceShared()) // CloudletSchedulerSpaceShared is used by default because of its performance. If necessary it is overwritten in the simulation
  }

  /**
   * Predicates that is used to check if the VM is overaloaded. Used by HorizontalScalingSimple.
   *
   * @param vm the VM to be checked
   * @return true if the VM is overloaded, false otherwise
   */
  def isVmOverloaded(vm: Vm): Boolean = {
    val cpuOverloadingThreshold = config.getDouble("vm.autoscaling.horizontalScaling.cpuOverloadedThreshold")
    vm.getCpuPercentUtilization() > cpuOverloadingThreshold
  }

  /**
   * This method creates an instance of HorizontalVmScalingSimple, used to implement horizontal scaling of VMs.
   *
   * @param vm the VM to which the autoscaling policy must be applied
   * @return the VM with the autoscaling policy applied
   */
  def buildHorizontalScaling(vm: Vm): Vm = {
    val horizontalScaling: HorizontalVmScalingSimple = new HorizontalVmScalingSimple();
    horizontalScaling.setVmSupplier(() => createVm()).setOverloadPredicate(isVmOverloaded)
    vm.setHorizontalScaling(horizontalScaling)
  }

  /**
   * This method creates an instance of VerticalVmScalingSimple, used to implement vertical scaling of VMs (CPU, RAM, bandwidth).
   *
   * @param vm the VM to which the autoscaling policy must be applied
   * @return the VM with the autoscaling policy applied
   */
  def buildVerticalScaling(vm: Vm): Vm = {
    val cpuVerticalScalingEnabled: Boolean = config.getInt("vm.autoscaling.cpuVerticalScaling.enabled") == 1
    val ramVerticalScalingEnabled: Boolean = config.getInt("vm.autoscaling.ramVerticalScaling.enabled") == 1
    val bwVerticalScalingEnabled: Boolean = config.getInt("vm.autoscaling.bwVerticalScaling.enabled") == 1

    if (cpuVerticalScalingEnabled) {
      val cpuVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Processor], config.getDouble("vm.autoscaling.cpuVerticalScaling.scalingFactor"))
      cpuVerticalScaling.setResourceScaling((vs) => 2 * vs.getScalingFactor * vs.getAllocatedResource)

      val lowerThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = cpuLowerUtilizationThreshold()
      }

      val upperThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = cpuUpperUtilizationThreshold()
      }

      cpuVerticalScaling.setLowerThresholdFunction(lowerThresholdFunction)
      cpuVerticalScaling.setUpperThresholdFunction(upperThresholdFunction)

      vm.setPeVerticalScaling(cpuVerticalScaling);
    }

    if (ramVerticalScalingEnabled) {
      val ramVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Ram], config.getDouble("vm.autoscaling.ramVerticalScaling.scalingFactor"))
      ramVerticalScaling.setResourceScaling(new ResourceScalingGradual()) // Here we are not using an instantaneous resource scaling technique since we can tolerate some loss in SLA to avoid unneeded VM scaling

      val lowerThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = ramLowerUtilizationThreshold()
      }

      val upperThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = ramUpperUtilizationThreshold()
      }

      ramVerticalScaling.setLowerThresholdFunction(lowerThresholdFunction)
      ramVerticalScaling.setUpperThresholdFunction(upperThresholdFunction)

      vm.setRamVerticalScaling(ramVerticalScaling);
    }

    if (bwVerticalScalingEnabled) {
      val bwVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Bandwidth], config.getDouble("vm.autoscaling.ramVerticalScaling.scalingFactor"))
      bwVerticalScaling.setResourceScaling(new ResourceScalingGradual()) // Here we are not using an instantaneous resource scaling technique since we can tolerate some loss in SLA to avoid unneeded VM scaling

      val lowerThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = bwLowerUtilizationThreshold()
      }

      val upperThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = bwUpperUtilizationThreshold()
      }

      bwVerticalScaling.setLowerThresholdFunction(lowerThresholdFunction)
      bwVerticalScaling.setUpperThresholdFunction(upperThresholdFunction)

      vm.setBwVerticalScaling(bwVerticalScaling);
    }

    return vm
  }

  /**
   * Method used to determine the threshold to check if the vm CPU is underloaded
   *
   * @return the lower threshold of utilization of the VM CPU
   */
  def cpuLowerUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.cpuVerticalScaling.lowerUtilizationThreshold")
  }

  /**
   * Method used to determine the threshold to check if the vm CPU is overloaded
   *
   * @return the upper threshold of utilization of the VM CPU
   */
  def cpuUpperUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.cpuVerticalScaling.upperUtilizationThreshold")
  }

  /**
   * Method used to determine the threshold to check if the vm RAM is underloaded
   *
   * @return the lower threshold of utilization of the VM RAM
   */
  def ramLowerUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.ramVerticalScaling.lowerUtilizationThreshold")
  }

  /**
   * Method used to determine the threshold to check if the vm RAM is overloaded
   *
   * @return the upper threshold of utilization of the VM RAM
   */
  def ramUpperUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.ramVerticalScaling.upperUtilizationThreshold")
  }

  /**
   * Method used to determine the threshold to check if the vm bandwidth is underloaded
   *
   * @return the lower threshold of utilization of the VM bandwidth
   */
  def bwLowerUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.bwVerticalScaling.lowerUtilizationThreshold")
  }

  /**
   * Method used to determine the threshold to check if the vm bandwidth is overloaded
   *
   * @return the upper threshold of utilization of the VM bandwidth
   */
  def bwUpperUtilizationThreshold(): Double = {
    return config.getDouble("vm.autoscaling.bwVerticalScaling.upperUtilizationThreshold")
  }

  /**
   * Maps the scaling strategy Int value that can be found in configuration to a value of the ScalingStrategy Enum
   *
   * @param id the Integer found in the configuration (vm.autoscaling.scalingStrategy)
   * @return the correct value of the Enum for the current configuration
   */
  def mapScalingIdToStrategy(id: Int): ScalingStrategy = {
    id match {
      case 0 => ScalingStrategy.HORIZONTAL
      case 1 => ScalingStrategy.VERTICAL
      case 2 => ScalingStrategy.BOTH
      case _ => ScalingStrategy.NONE
    }
  }
}
