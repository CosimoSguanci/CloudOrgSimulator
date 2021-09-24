package Utils

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

object CommonMethods {
  def createDatacenters(config: Config, simulation: CloudSim, datacenters: List[NetworkDatacenter], numOfDatacenters: Int, vmAllocation: VmAllocationType, vmScheduler: VmSchedulerType): List[NetworkDatacenter] = {

    if (datacenters.length == numOfDatacenters) {
      return datacenters
    }

    val innerDatacenterList: List[NetworkDatacenter] = if (datacenters != null) then datacenters :+ createDatacenter(config, simulation, vmAllocation, vmScheduler) else List(createDatacenter(config, simulation, vmAllocation, vmScheduler));

    createDatacenters(config, simulation, innerDatacenterList, numOfDatacenters, vmAllocation, vmScheduler);
  }

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

    //datacenter.setSchedulingInterval(100)
    return datacenter
  }

  def createHost(config: Config, vmScheduler: VmSchedulerAbstract): Host = {
    val numOfHostPEs = config.getInt("host.PEs")
    val range = 1 to numOfHostPEs
    val peList: List[Pe] = range.map(i => new PeSimple(config.getInt("host.mipsCapacityPE"))).toList

    val hostRam = config.getLong("host.RAMInMBs")
    val hostBw = config.getLong("host.BandwidthInMBps")
    val hostStorage = config.getLong("host.StorageInMBs")
    val host = new HostSimple(hostRam, hostBw, hostStorage, peList.asJava) // default vm scheduler: VmSchedulerSpaceShared

    host
      .setRamProvisioner(new ResourceProvisionerSimple())
      .setBwProvisioner(new ResourceProvisionerSimple())
      .setVmScheduler(vmScheduler)
  }

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

  def addFilesToSan(config: Config, san: SanStorage, numOfStoredFiles: Int): Unit = {
    //val numOfStoredFiles = config.getInt("cloudlet.numStorageCloudlets") // for every cloudlet relative to the cloud storage service, we add a file for the simulation
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

  private def getVmAllocation(vmAllocationType: VmAllocationType): VmAllocationPolicyAbstract = {
    vmAllocationType match {
      case VmAllocationType.VM_ALLOCATION_SIMPLE => new VmAllocationPolicySimple()
      case VmAllocationType.VM_ALLOCATION_BEST_FIT => new VmAllocationPolicyBestFit()
    }
  }

  private def getVmScheduler(vmSchedulerType: VmSchedulerType): VmSchedulerAbstract = {
    vmSchedulerType match {
      case VmSchedulerType.VM_SCHEDULER_SPACE_SHARED => new VmSchedulerSpaceShared()
      case VmSchedulerType.VM_SCHEDULER_TIME_SHARED => new VmSchedulerTimeShared()
    }
  }

  private def getCloudletScheduler(cloudletSchedulerType: CloudletSchedulerType): CloudletSchedulerAbstract = {
    cloudletSchedulerType match {
      case CloudletSchedulerType.CLOUDLET_SCHEDULER_SPACE_SHARED => new CloudletSchedulerSpaceShared()
      case CloudletSchedulerType.CLOUDLET_SCHEDULER_TIME_SHARED => new CloudletSchedulerTimeShared()
    }
  }

  private def getUtilizationModel(utilizationModelType: UtilizationModelType, config: Config): UtilizationModelAbstract = {
    utilizationModelType match {
      case UtilizationModelType.FULL => new UtilizationModelFull()
      case UtilizationModelType.DYNAMIC => new UtilizationModelDynamic(config.getDouble("utilizationRatio"))
      case UtilizationModelType.STOCHASTIC => new UtilizationModelStochastic()
    }
  }

  def createVmsSaaS(config: Config, cloudletScheduler: CloudletSchedulerType): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfVMs = config.getInt("vm.num")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(scalingStrategyId).setCloudletScheduler(getCloudletScheduler(cloudletScheduler))).toList
  }

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

  def setupFileRequirementsForCloudletsSaaS(config: Config, cloudletList: List[WorkspaceCloudlet]): Unit = {
    val numOfStoredFiles = config.getInt("cloudlet.numStorageCloudlets")
    val range = 1 to numOfStoredFiles

    cloudletList.filter(c => c.typeOfService == TypeOfService.CLOUD_STORAGE).lazyZip(range).map {
      (cloudlet, i) => {
        cloudlet.addRequiredFile(s"file$i.txt");
      }
    }
  }

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
    //println(vmCost)

    val newTotalCost: Double = totalCost + vmCost.getTotalCost()
    val newProcessingTotalCost: Double = processingTotalCost + vmCost.getProcessingCost()
    val newMemoryTotalCost: Double = memoryTotalCost + vmCost.getMemoryCost()
    val newStorageTotalCost: Double = storageTotalCost + vmCost.getStorageCost()
    val newBwTotalCost: Double = bwTotalCost + vmCost.getBwCost()
    val newTotalNonIdleVms: Int = if (vm.getTotalExecutionTime() > 0) then totalNonIdleVms + 1 else totalNonIdleVms

    printCost(i + 1, broker, newTotalCost, newProcessingTotalCost, newMemoryTotalCost, newStorageTotalCost, newBwTotalCost, newTotalNonIdleVms)
  }

  // IAAS PAAS FAAS

  def createVmsIaaSPaaS(config: Config): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfStandardVMs = config.getInt("vm.standardVms.num")
    //val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations

    (1 to numOfStandardVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.PEs"))).toList
    //val faasVms = (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs")).setCloudletScheduler(new CloudletSchedulerSpaceShared())).toList

    //standardVms ::: faasVms
  }

  def createVmsFaaS(config: Config): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations
    (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs"))).toList
  }

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

  def createCloudletsFaaS(config: Config): List[IaasPaasFaasCloudlet] = {
    val numOfFaasCloudlets = config.getInt("cloudlet.faas.num")
    val faasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfFaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList
    faasCloudlets.foreach(c => c.setupComputingResources())
    return faasCloudlets
  }
}
