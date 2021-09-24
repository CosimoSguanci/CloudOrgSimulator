package Simulations.SaaS

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{ScalingStrategy, TypeOfService, VmWithScalingFactory, WorkspaceCloudlet}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRandom, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.{DatacenterBrokerBestFit, DatacenterBrokerFirstFit, DatacenterBrokerHeuristic, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.distributions.{ContinuousDistribution, UniformDistr}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerCompletelyFair, CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}

import scala.::
import scala.jdk.CollectionConverters.*

// TODO: Power Usage (vmallocationroundrobin?), combination of all the simulations in oeìne datacenter, lazyZip indexes

class SaasWorkspaceSimulationVmSchedulerTimeShared

object SaasWorkspaceSimulationVmSchedulerTimeShared:

  val CONFIG = "saasSimulationWorkspaceVmSchedulerTimeShared";

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[SaasWorkspaceSimulationBasic])

  def Start() = {
    val simulation = new CloudSim()

    val numOfDatacenters = config.getInt("datacenter.num")

    val datacenters: List[Datacenter] = createDatacenters(simulation, List.empty, numOfDatacenters)
    val datacenterStorageList: List[DatacenterStorage] = createDatacenterStorage(numOfDatacenters);

    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }

    val vmList: List[Vm] = createVms()
    val cloudletlist: List[WorkspaceCloudlet] = createCloudlets()

    setupFileRequirementsForCloudlets(cloudletlist);

    val broker0 = new DatacenterBrokerSimple(simulation)

    broker0.submitVmList(vmList.asJava)
    broker0.submitCloudletList(cloudletlist.asJava)

    simulation.start();

    val finishedCloudlets = broker0.getCloudletFinishedList()

    val cloudletsTableBuilder: CloudletsTableBuilder = new CloudletsTableBuilder(finishedCloudlets);

    cloudletsTableBuilder.addColumn(1, new TextTableColumn("CloudletType", ""), new java.util.function.Function[Cloudlet, Object] {
      override def apply(c: Cloudlet): Object = {
        cloudletlist.filter(p => p.equals(c)).map(a => a.getTypeOfServiceText())(0)
      }
    }).build();


    printCost(i = 0, broker0, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
  }

  def createDatacenters(simulation: CloudSim, datacenters: List[NetworkDatacenter], numOfDatacenters: Int): List[NetworkDatacenter] = {

    if (datacenters.length == numOfDatacenters) {
      return datacenters
    }

    val innerDatacenterList: List[NetworkDatacenter] = if (datacenters != null) then datacenters :+ createDatacenter(simulation) else List(createDatacenter(simulation));

    createDatacenters(simulation, innerDatacenterList, numOfDatacenters);
  }

  def createDatacenter(simulation: CloudSim): NetworkDatacenter = {
    val numOfHosts = config.getInt("host.num")
    val range = 1 to numOfHosts
    val hostList: List[Host] = range.map(i => createHost()).toList

    val datacenter: NetworkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicyBestFit())

    datacenter
      .getCharacteristics()
      .setCostPerSecond(config.getDouble("datacenter.costPerSecond"))
      .setCostPerMem(config.getDouble("datacenter.costPerMem"))
      .setCostPerStorage(config.getDouble("datacenter.costPerStorage"))
      .setCostPerBw(config.getDouble("datacenter.costPerBw"));

    //datacenter.setSchedulingInterval(100)
    return datacenter
  }

  def createHost(): Host = {
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
      .setVmScheduler(new VmSchedulerTimeShared())// timeshared/spaceshared?

    // VmSchedulerTime Shared -> MIPS VM = MIPS HOST TOTAL, BUT #PESVM SUPERIORE
  }

  def createDatacenterStorage(numOfDatacenters: Int): List[DatacenterStorage] = {
    val numOfSanForDatacenter = config.getInt("datacenter.numOfSanForDatacenter")
    val sanStorageCapacity = config.getLong("datacenter.sanStorageCapacityForDatacenter")
    val sanBW = config.getDouble("datacenter.sanBWInMbps")
    val sanNetworkLatency = config.getDouble("datacenter.sanNetworkLatencySec")

    val range = 1 to numOfDatacenters

    range.map(i => {
      val sanRange = 1 to numOfSanForDatacenter
      val sanList: List[SanStorage] = sanRange.map(j => new SanStorage(sanStorageCapacity, sanBW, sanNetworkLatency)).toList
      sanList.foreach(san => addFilesToSan(san));
      new DatacenterStorage(sanList.asJava)
    }).toList
  }

  def addFilesToSan(san: SanStorage): Unit = {
    //val numOfStoredFiles = config.getInt("datacenter.numOfStoredFiles")
    val numOfStoredFiles = config.getInt("cloudlet.numStorageCloudlets") // for every cloudlet relative to the cloud storage service, we add a file for the simulation
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

  def createVms(): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfVMs = config.getInt("vm.num")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(scalingStrategyId, vmPEs = config.getInt("vm.PEs"), vmMips = config.getInt("vm.vmMips")).setCloudletScheduler(new CloudletSchedulerSpaceShared())).toList
  }

  def createCloudlets(): List[WorkspaceCloudlet] = {
    val numOfEmailCloudlets = config.getInt("cloudlet.numEmailCloudlets")
    val numOfDocsCloudlets = config.getInt("cloudlet.numDocsCloudlets")
    val numOfStorageCloudlets = config.getInt("cloudlet.numStorageCloudlets")

    val emailCloudlets: List[WorkspaceCloudlet] = (1 to numOfEmailCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.EMAIL)).toList
    val docsCloudlets: List[WorkspaceCloudlet] = (1 to numOfDocsCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.CLOUD_DOCS)).toList
    val storageCloudlets: List[WorkspaceCloudlet] = (1 to numOfStorageCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.CLOUD_STORAGE)).toList

    emailCloudlets ::: docsCloudlets ::: storageCloudlets
  }

  /*  def createCloudlets(): List[Cloudlet] = {
      val numOfCloudlets = config.getInt("cloudlet.num")
      val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(config.getDouble("utilizationRatio"))
      val cloudletLength = config.getInt("cloudlet.length")
      val cloudletPEs = config.getInt("cloudlet.PEs")
      (1 to numOfCloudlets).map(i => new CloudletSimple(cloudletLength, cloudletPEs, utilizationModel)).toList
    }

    def createCloudletsTest(): List[WorkspaceCloudlet] = {
      val numOfCloudlets = config.getInt("cloudlet.num")
      (1 to numOfCloudlets).map(i => new WorkspaceCloudlet(TypeOfService.EMAIL)).toList
    }

    def createCloudlets2(): List[WorkspaceCloudlet] = {
      val numOfCloudlets = config.getInt("cloudlet.num")
      val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(config.getDouble("utilizationRatio"))
      val cloudletLength = config.getInt("cloudlet.length")
      val cloudletPEs = config.getInt("cloudlet.PEs")
      (1 to numOfCloudlets).map(i => new CloudletSimple(cloudletLength, cloudletPEs, utilizationModel)).toList
    }*/

  /*  def createRandomCloudlets(evt: EventInfo): Unit = { // dynamic arrival of new tasks to be processed
      val probabilityOfNewArrival = config.getDouble("cloudlet.probabilityOfNewArrival");
      if(random.sample() <= probabilityOfNewArrival) {
        //log

      }
    }*/

  def setupFileRequirementsForCloudlets(cloudletList: List[WorkspaceCloudlet]): Unit = {
    //val numOfStoredFiles = config.getInt("datacenter.numOfStoredFiles")
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

