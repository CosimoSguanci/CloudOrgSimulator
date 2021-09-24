package Utils

import Utils.CloudletTypes.*
import Utils.PolicyEnums.{CloudletSchedulerType, UtilizationModelType, VmAllocationType, VmSchedulerType}
import com.typesafe.config.Config
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyAbstract, VmAllocationPolicyBestFit, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerAbstract, CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerAbstract, VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelAbstract, UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}
import org.cloudbus.cloudsim.vms.{Vm, VmCost}

import scala.jdk.CollectionConverters.*

/**
 * Singleton used to share common methods and avoid code duplication across simulations
 */
object CommonMethods {

  /**
   * Create the list of Datacenters needed for the simulation
   *
   * @param config           The configuration corresponding to the current simulation
   * @param simulation       The current simulation
   * @param datacenters      Partial list of datacenters (the method is recursive to follow functional programming best practices)
   * @param numOfDatacenters The total number of datacenters to be created
   * @param vmAllocation     The policy that will be used to place VMs in Hosts
   * @param vmScheduler      The policy that will be used to share processing power of a Host among VMs
   * @return the list of datacenters of the system
   */
  def createDatacenters(config: Config, simulation: CloudSim, datacenters: List[NetworkDatacenter], numOfDatacenters: Int, vmAllocation: VmAllocationType, vmScheduler: VmSchedulerType): List[NetworkDatacenter] = {

    if (datacenters.length == numOfDatacenters) {
      return datacenters
    }

    val innerDatacenterList: List[NetworkDatacenter] = if (datacenters != null) then datacenters :+ createDatacenter(config, simulation, vmAllocation, vmScheduler) else List(createDatacenter(config, simulation, vmAllocation, vmScheduler));

    createDatacenters(config, simulation, innerDatacenterList, numOfDatacenters, vmAllocation, vmScheduler);
  }

  /**
   * Creates a single datacenter given the parameters in the configuration
   *
   * @param config       The configuration corresponding to the current simulation
   * @param simulation   The current simulation
   * @param vmAllocation The policy that will be used to place VMs in Hosts
   * @param vmScheduler  The policy that will be used to share processing power of a Host among VMs
   * @return the newly created instance of Datacenter
   */
  def createDatacenter(config: Config, simulation: CloudSim, vmAllocation: VmAllocationType, vmScheduler: VmSchedulerType): NetworkDatacenter = {
    val numOfHosts = config.getInt("host.num")
    val range = 1 to numOfHosts
    val hostList: List[Host] = range.map(i => createHost(config, getVmScheduler(vmScheduler))).toList

    val datacenter: NetworkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, getVmAllocation(vmAllocation))

    datacenter
      .getCharacteristics()
      .setCostPerSecond(config.getDouble("datacenter.costPerSecond"))
      .setCostPerMem(config.getDouble("datacenter.costPerMem"))
      .setCostPerStorage(config.getDouble("datacenter.costPerStorage"))
      .setCostPerBw(config.getDouble("datacenter.costPerBw"));

    return datacenter
  }

  /**
   * Creates an host to be placed inside a datacenter
   *
   * @param config      The configuration corresponding to the current simulation
   * @param vmScheduler The policy that will be used to share processing power of a Host among VMs
   * @return the newly created host
   */
  def createHost(config: Config, vmScheduler: VmSchedulerAbstract): Host = {
    val numOfHostPEs = config.getInt("host.PEs")
    val range = 1 to numOfHostPEs
    val peList: List[Pe] = range.map(i => new PeSimple(config.getInt("host.mipsCapacityPE"))).toList

    val hostRam = config.getLong("host.RAMInMBs")
    val hostBw = config.getLong("host.BandwidthInMBps")
    val hostStorage = config.getLong("host.StorageInMBs")
    val host = new HostSimple(hostRam, hostBw, hostStorage, peList.asJava)

    host
      .setRamProvisioner(new ResourceProvisionerSimple())
      .setBwProvisioner(new ResourceProvisionerSimple())
      .setVmScheduler(vmScheduler)
  }

  /**
   * Creates the storage organization of all the datacenters in the system
   *
   * @param config           The configuration corresponding to the current simulation
   * @param numOfDatacenters The total number of datacenters
   * @param numOfStoredFiles The total number of files to be added to a SAN
   * @return the list of datacenter storage for all the datacenters
   */
  def createDatacenterStorage(config: Config, numOfDatacenters: Int, numOfStoredFiles: Int): List[DatacenterStorage] = {
    val numOfSanForDatacenter = config.getInt("datacenter.numOfSanForDatacenter")
    val sanStorageCapacity = config.getLong("datacenter.sanStorageCapacityForDatacenter")
    val sanBW = config.getDouble("datacenter.sanBWInMbps")
    val sanNetworkLatency = config.getDouble("datacenter.sanNetworkLatencySec")

    val range = 1 to numOfDatacenters

    range.map(i => {
      val sanRange = 1 to numOfSanForDatacenter
      val sanList: List[SanStorage] = sanRange.map(j => new SanStorage(sanStorageCapacity, sanBW, sanNetworkLatency)).toList
      sanList.foreach(san => addFilesToSan(config, san, numOfStoredFiles));
      new DatacenterStorage(sanList.asJava)
    }).toList
  }

  /**
   * Adds the files needed for a simulation to a SAN
   *
   * @param config           The configuration corresponding to the current simulation
   * @param san              The SAN in which the files must be placed
   * @param numOfStoredFiles The total number of files to be added to the SAN
   */
  def addFilesToSan(config: Config, san: SanStorage, numOfStoredFiles: Int): Unit = {
    val sizeSmall = config.getInt("datacenter.sanFileSizeInMB_small")
    val sizeMedium = config.getInt("datacenter.sanFileSizeInMB_medium")
    val sizeBig = config.getInt("datacenter.sanFileSizeInMB_big")

    val range = 1 to numOfStoredFiles

    range.foreach(i => {
      val size = if (i < (numOfStoredFiles / 3)) then sizeSmall else if (i < 2 * (numOfStoredFiles / 3)) then sizeMedium else sizeBig
      val file = new File(s"file$i.txt", size);
      san.addFile(file);
    })
  }

  /**
   * Maps VmAllocationType values to actual VmAllocation policies
   *
   * @param vmAllocationType the value of the VmAllocationType enum to be mapped
   * @return an instance of VmAllocation
   */
  private def getVmAllocation(vmAllocationType: VmAllocationType): VmAllocationPolicyAbstract = {
    vmAllocationType match {
      case VmAllocationType.VM_ALLOCATION_SIMPLE => new VmAllocationPolicySimple()
      case VmAllocationType.VM_ALLOCATION_BEST_FIT => new VmAllocationPolicyBestFit()
    }
  }

  /**
   * Maps VmSchedulerType values to actual VmScheduler policies
   *
   * @param vmSchedulerType the value of the VmSchedulerType enum to be mapped
   * @return an instance of VmScheduler
   */
  private def getVmScheduler(vmSchedulerType: VmSchedulerType): VmSchedulerAbstract = {
    vmSchedulerType match {
      case VmSchedulerType.VM_SCHEDULER_SPACE_SHARED => new VmSchedulerSpaceShared()
      case VmSchedulerType.VM_SCHEDULER_TIME_SHARED => new VmSchedulerTimeShared()
    }
  }

  /**
   * Maps CloudletSchedulerType values to actual CloudletScheduler policies
   *
   * @param cloudletSchedulerType the value of the CloudletSchedulerType enum to be mapped
   * @return an instance of CloudletScheduler
   */
  private def getCloudletScheduler(cloudletSchedulerType: CloudletSchedulerType): CloudletSchedulerAbstract = {
    cloudletSchedulerType match {
      case CloudletSchedulerType.CLOUDLET_SCHEDULER_SPACE_SHARED => new CloudletSchedulerSpaceShared()
      case CloudletSchedulerType.CLOUDLET_SCHEDULER_TIME_SHARED => new CloudletSchedulerTimeShared()
    }
  }

  /**
   * Maps UtilizationModelType values to actual UtilizationModel policies
   *
   * @param utilizationModelType the value of the UtilizationModelType enum to be mapped
   * @param config               The configuration corresponding to the current simulation
   * @return an instance of UtilizationModel
   */
  private def getUtilizationModel(utilizationModelType: UtilizationModelType, config: Config): UtilizationModelAbstract = {
    utilizationModelType match {
      case UtilizationModelType.FULL => new UtilizationModelFull()
      case UtilizationModelType.DYNAMIC => new UtilizationModelDynamic(config.getDouble("utilizationRatio"))
      case UtilizationModelType.STOCHASTIC => new UtilizationModelStochastic()
    }
  }

  /**
   * Creates VMs for SaaS simulations
   *
   * @param config            The configuration corresponding to the current simulation
   * @param cloudletScheduler the scheduler to be used by VMs to execute Cloudlets
   * @return the list of newly created VMs
   */
  def createVmsSaaS(config: Config, cloudletScheduler: CloudletSchedulerType): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfVMs = config.getInt("vm.num")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(scalingStrategyId).setCloudletScheduler(getCloudletScheduler(cloudletScheduler))).toList
  }

  /**
   * Creates Cloudlets for SaaS simulations
   *
   * @param config The configuration corresponding to the current simulation
   * @return the list of newly created Cloudlets
   */
  def createCloudletsSaaS(config: Config): List[WorkspaceCloudlet] = {
    val numOfEmailCloudlets = config.getInt("cloudlet.numEmailCloudlets")
    val numOfDocsCloudlets = config.getInt("cloudlet.numDocsCloudlets")
    val numOfStorageCloudlets = config.getInt("cloudlet.numStorageCloudlets")

    val emailCloudlets: List[WorkspaceCloudlet] = (1 to numOfEmailCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.EMAIL)).toList
    val docsCloudlets: List[WorkspaceCloudlet] = (1 to numOfDocsCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.CLOUD_DOCS)).toList
    val storageCloudlets: List[WorkspaceCloudlet] = (1 to numOfStorageCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.CLOUD_STORAGE)).toList

    emailCloudlets.foreach(c =>
      c.setNumberOfPes(config.getInt("cloudlet.PEs"))
        .setLength(config.getInt("cloudlet.length"))
        .setUtilizationModel(new UtilizationModelDynamic(config.getDouble("utilizationRatio"))))

    docsCloudlets.foreach(c =>
      c.setNumberOfPes(config.getInt("cloudlet.PEs"))
        .setLength(config.getInt("cloudlet.length"))
        .setUtilizationModel(new UtilizationModelDynamic(config.getDouble("utilizationRatio"))))

    storageCloudlets.foreach(c =>
      c.setNumberOfPes(config.getInt("cloudlet.PEs"))
        .setLength(config.getInt("cloudlet.length"))
        .setUtilizationModel(new UtilizationModelDynamic(config.getDouble("utilizationRatio"))))

    emailCloudlets ::: docsCloudlets ::: storageCloudlets
  }

  /**
   * Sets the file requirements for SaaS Cloudlets (i.e., for the cloudlets that implement Cloud Storage)
   *
   * @param config       The configuration corresponding to the current simulation
   * @param cloudletList The list of Cloudlets in the system
   */
  def setupFileRequirementsForCloudletsSaaS(config: Config, cloudletList: List[WorkspaceCloudlet]): Unit = {
    val numOfStoredFiles = config.getInt("cloudlet.numStorageCloudlets")
    val range = 1 to numOfStoredFiles

    // We filter to obtain only the Cloud Storage cloudlets and assign file requirements to them
    cloudletList.filter(c => c.typeOfService == TypeOfService.CLOUD_STORAGE).lazyZip(range).map {
      (cloudlet, i) => {
        cloudlet.addRequiredFile(s"file$i.txt");
      }
    }
  }

  /**
   * Utility function used to print final costs for VMs
   *
   * @param i                   index in VM list (recursive)
   * @param broker              the broker whose VM must be considered
   * @param totalCost           partial total cost of VMs
   * @param processingTotalCost partial total cost of VMs CPUs
   * @param memoryTotalCost     partial total cost of VMs RAM
   * @param storageTotalCost    partial total cost of VMs storage
   * @param bwTotalCost         partial total cost of VMs bandwidth
   * @param totalNonIdleVms     partial total number of VMs that were active in the simulation
   */
  def printCost(
                 i: Int,
                 broker: DatacenterBrokerSimple,
                 totalCost: Double,
                 processingTotalCost: Double,
                 memoryTotalCost: Double,
                 storageTotalCost: Double,
                 bwTotalCost: Double,
                 totalNonIdleVms: Int): Unit = {

    if (i == broker.getVmCreatedList().size()) {
      // finished iterating over vms, we print the final report
      println("Total cost ($) for " + totalNonIdleVms +
        " created VMs: \n" +
        "processing cost: " + processingTotalCost + "$ \n" +
        "memory cost: " + memoryTotalCost + "$ \n" +
        "storage cost: " + storageTotalCost + "$ \n" +
        "bandwidth cost: " + bwTotalCost + "$ \n" +
        "total cost: " + totalCost + "$")

      return;
    }

    val vm: Vm = broker.getVmCreatedList().get(i);
    val vmCost: VmCost = new VmCost(vm)

    val newTotalCost: Double = totalCost + vmCost.getTotalCost()
    val newProcessingTotalCost: Double = processingTotalCost + vmCost.getProcessingCost()
    val newMemoryTotalCost: Double = memoryTotalCost + vmCost.getMemoryCost()
    val newStorageTotalCost: Double = storageTotalCost + vmCost.getStorageCost()
    val newBwTotalCost: Double = bwTotalCost + vmCost.getBwCost()
    val newTotalNonIdleVms: Int = if (vm.getTotalExecutionTime() > 0) then totalNonIdleVms + 1 else totalNonIdleVms

    printCost(i + 1, broker, newTotalCost, newProcessingTotalCost, newMemoryTotalCost, newStorageTotalCost, newBwTotalCost, newTotalNonIdleVms)
  }

  /**
   * Creates VMs for IaaS/PaaS simulations
   *
   * @param config The configuration corresponding to the current simulation
   * @return the newly created VMs
   */
  def createVmsIaaSPaaS(config: Config): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfStandardVMs = config.getInt("vm.standardVms.num")
    (1 to numOfStandardVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.PEs"))).toList
  }

  /**
   * Creates VMs for FaaS simulations
   *
   * @param config The configuration corresponding to the current simulation
   * @return the newly created VMs
   */
  def createVmsFaaS(config: Config): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations
    (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs"))).toList
  }

  /**
   * Create Cloudlets for IaaS/PaaS simulations
   *
   * @param config               The configuration corresponding to the current simulation
   * @param utilizationModelType The UtilizationModel to be used for cloudlets in the simulation
   * @return the newly creates Cloudlets
   */
  def createCloudletsIaaSPaaS(config: Config, utilizationModelType: UtilizationModelType): List[IaasPaasFaasCloudlet] = {
    val numOfIaasCloudlets = config.getInt("cloudlet.iaas.num")
    val numOfPaasCloudlets = config.getInt("cloudlet.paas.num")

    val iaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfIaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.IAAS)).toList
    val paasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfPaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.PAAS)).toList

    iaasCloudlets.foreach(c => c.setupComputingResources())
    paasCloudlets.foreach(c => c.setupComputingResources())

    iaasCloudlets.foreach(c => c.setUtilizationModel(getUtilizationModel(utilizationModelType, config)))
    paasCloudlets.foreach(c => c.setUtilizationModel(getUtilizationModel(utilizationModelType, config)))

    iaasCloudlets ::: paasCloudlets
  }

  /**
   * Create Cloudlets for FaaS simulations
   *
   * @param config The configuration corresponding to the current simulation
   * @return the newly creates Cloudlets
   */
  def createCloudletsFaaS(config: Config): List[IaasPaasFaasCloudlet] = {
    val numOfFaasCloudlets = config.getInt("cloudlet.faas.num")
    val faasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfFaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList
    faasCloudlets.foreach(c => c.setupComputingResources())
    return faasCloudlets
  }
}
