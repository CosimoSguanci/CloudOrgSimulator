package Utils

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Simulations.SimulationSuiteServices.config
import org.cloudbus.cloudsim.resources.{Bandwidth, Processor, Ram}
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerTimeShared, CloudletSchedulerSpaceShared}
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.autoscaling.resources.{ResourceScalingGradual, ResourceScalingInstantaneous}
import org.cloudsimplus.autoscaling.{HorizontalVmScalingSimple, VerticalVmScalingSimple, VmScaling}
import org.cloudsimplus.listeners.VmHostEventInfo

import java.util.function
import java.util.function.{Predicate, Supplier}

enum ScalingStrategy:
  case HORIZONTAL, VERTICAL, BOTH, NONE

object VmWithScalingFactory {
  def apply(scalingStrategyId: Int): Vm = { // vm storage?

    /*    val config = ObtainConfigReference("saasSimulation") match {
          case Some(value) => value
          case None => throw new RuntimeException("Cannot obtain a reference to the config data.")
        }
        val logger = CreateLogger(classOf[BasicCloudSimPlusExample])*/

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

  def createVm(): Vm = {
    val hostMips = config.getInt("saasSimulation.host.mipsCapacity")
    val vmPEs = config.getInt("saasSimulation.vm.PEs")
    val ramInMBs = config.getInt("saasSimulation.vm.RAMInMBs")
    val storageInMBs = config.getLong("saasSimulation.vm.StorageInMBs")
    val bwInMBps = config.getInt("saasSimulation.vm.BandwidthInMBps")

    new VmSimple(hostMips, vmPEs)
      .setRam(ramInMBs)
      .setBw(bwInMBps)
      .setSize(storageInMBs)
      .setCloudletScheduler(new CloudletSchedulerSpaceShared()) // default is time shared and is much less efficient in this case
  }

  def isVmOverloaded(vm: Vm): Boolean = {
    val cpuOverloadingThreshold = config.getDouble("saasSimulation.vm.autoscaling.horizontalScaling.cpuOverloadedThreshold")
    vm.getCpuPercentUtilization() > cpuOverloadingThreshold
  }

  def buildHorizontalScaling(vm: Vm): Vm = {
    val horizontalScaling: HorizontalVmScalingSimple = new HorizontalVmScalingSimple();
/*    horizontalScaling
      .setVmSupplier(new Supplier[Vm] {
        override def get(): Vm = createVm()
      })
      .setOverloadPredicate(new Predicate[Vm] {
        override def test(t: Vm): Boolean = isVmOverloaded(t)
      })*/
    horizontalScaling.setVmSupplier(() => createVm()).setOverloadPredicate(isVmOverloaded)
    vm.setHorizontalScaling(horizontalScaling)
  }

  def buildVerticalScaling(vm: Vm): Vm = {
    val cpuVerticalScalingEnabled: Boolean = config.getInt("saasSimulation.vm.autoscaling.cpuVerticalScaling.enabled") == 1
    val ramVerticalScalingEnabled: Boolean = config.getInt("saasSimulation.vm.autoscaling.ramVerticalScaling.enabled") == 1
    val bwVerticalScalingEnabled: Boolean = config.getInt("saasSimulation.vm.autoscaling.bwVerticalScaling.enabled") == 1

    if (cpuVerticalScalingEnabled) {
      val cpuVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Processor], config.getDouble("saasSimulation.vm.autoscaling.cpuVerticalScaling.scalingFactor"))
      cpuVerticalScaling.setResourceScaling(new ResourceScalingGradual()) // Here we are not using an instantaneous resource scaling technique since we can tolerate some loss in SLA to avoid unneeded VM scaling

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
      val ramVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Ram], config.getDouble("saasSimulation.vm.autoscaling.ramVerticalScaling.scalingFactor"))
      ramVerticalScaling.setResourceScaling(new ResourceScalingGradual()) // Here we are not using an instantaneous resource scaling technique since we can tolerate some loss in SLA to avoid unneeded VM scaling

      val lowerThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = ramLowerUtilizationThreshold()
      }

      val upperThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = ramUpperUtilizationThreshold()
      }

      ramVerticalScaling.setLowerThresholdFunction(lowerThresholdFunction)
      ramVerticalScaling.setUpperThresholdFunction(upperThresholdFunction)

      vm.setPeVerticalScaling(ramVerticalScaling);
    }

    if (bwVerticalScalingEnabled) {
      val bwVerticalScaling: VerticalVmScalingSimple = new VerticalVmScalingSimple(classOf[Bandwidth], config.getDouble("saasSimulation.vm.autoscaling.ramVerticalScaling.scalingFactor"))
      bwVerticalScaling.setResourceScaling(new ResourceScalingGradual()) // Here we are not using an instantaneous resource scaling technique since we can tolerate some loss in SLA to avoid unneeded VM scaling

      val lowerThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = bwLowerUtilizationThreshold()
      }

      val upperThresholdFunction: java.util.function.Function[Vm, java.lang.Double] = new java.util.function.Function[Vm, java.lang.Double] {
        override def apply(vm: Vm): java.lang.Double = bwUpperUtilizationThreshold()
      }

      bwVerticalScaling.setLowerThresholdFunction(lowerThresholdFunction)
      bwVerticalScaling.setUpperThresholdFunction(upperThresholdFunction)

      vm.setPeVerticalScaling(bwVerticalScaling);
    }

    return vm

  }

  // threshold to determine if the vm is underloaded
  def cpuLowerUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.cpuVerticalScaling.lowerUtilizationThreshold")
  }

  def cpuUpperUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.cpuVerticalScaling.upperUtilizationThreshold")
  }

  def ramLowerUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.ramVerticalScaling.lowerUtilizationThreshold")
  }

  def ramUpperUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.ramVerticalScaling.upperUtilizationThreshold")
  }

  def bwLowerUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.bwVerticalScaling.lowerUtilizationThreshold")
  }

  def bwUpperUtilizationThreshold(): Double = {
    return config.getDouble("saasSimulation.vm.autoscaling.bwVerticalScaling.upperUtilizationThreshold")
  }

  def mapScalingIdToStrategy(id: Int): ScalingStrategy = {
    id match {
      case 0 => ScalingStrategy.HORIZONTAL
      case 1 => ScalingStrategy.VERTICAL
      case 2 => ScalingStrategy.BOTH
      case _ => ScalingStrategy.NONE
    }
  }
}
