package Simulations.IaaS_PaaS_FaaS

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{DeploymentModel, IaasPaasFaasCloudlet, ScalingStrategy, TypeOfService, VmWithScalingFactory}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRandom, VmAllocationPolicyRoundRobin, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.{DatacenterBrokerBestFit, DatacenterBrokerFirstFit, DatacenterBrokerHeuristic, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.network.{CloudletExecutionTask, NetworkCloudlet}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.distributions.{ContinuousDistribution, UniformDistr}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.network.topologies.BriteNetworkTopology
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}

import java.util.Comparator
import scala.::
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class IaasPaasFaasSimulationBasic

object IaasPaasFaasSimulationBasic:

  val CONFIG = "iaasPaasFaasSimulationBasic";

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[IaasPaasFaasSimulationBasic])

  val vmList: List[Vm] = createVms()
  val cloudletList: List[IaasPaasFaasCloudlet] = createCloudlets()

  val faasVmList: List[Vm] = createFaasVms()
  val faasCloudletList: List[IaasPaasFaasCloudlet] = createFaasCloudlets()


  val simulation = new CloudSim()
  val broker0 = new DatacenterBrokerSimple(simulation)
  val brokerFaas = new DatacenterBrokerSimple(simulation)

  def Start() = {

    CloudletExecutionTask

    val numOfDatacenters = config.getInt("datacenter.num")

    val datacenters: List[Datacenter] = createDatacenters(simulation, List.empty, numOfDatacenters)
    val datacenterStorageList: List[DatacenterStorage] = createDatacenterStorage(numOfDatacenters);

    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }


    setupFileRequirementsForCloudlets(cloudletList);

    simulation.addOnClockTickListener(periodicEventHandler);

    broker0.setVmDestructionDelayFunction((vm) => config.getDouble("vm.destructionDelay"))
    brokerFaas.setVmDestructionDelayFunction((vm) => config.getDouble("vm.destructionDelay"))

    broker0.submitVmList(vmList.asJava)
    broker0.submitCloudletList(cloudletList.asJava)

    brokerFaas.submitVmList(faasVmList.asJava)
    brokerFaas.submitCloudletList(faasCloudletList.asJava)


    simulation.start();

    val finishedCloudlets = broker0.getCloudletFinishedList()

    finishedCloudlets.addAll(brokerFaas.getCloudletFinishedList())

    val cloudletsTableBuilder: CloudletsTableBuilder = new CloudletsTableBuilder(finishedCloudlets);

/*    cloudletsTableBuilder.addColumn(1, new TextTableColumn("CloudletType", ""), new java.util.function.Function[Cloudlet, Object] {
      override def apply(c: Cloudlet): Object = {
        //cloudletList.filter(p => p.equals(c)).map(a => a.getDeploymentModelText())(0)
        if(cloudletList.filter(p => p.equals(c)).nonEmpty) {
          cloudletList.filter(p => p.equals(c)).map(a => a.getDeploymentModelText())(0)
        }
        else {
          faasCloudletList.filter(p => p.equals(c)).map(a => a.getDeploymentModelText())(0)
        }
      }
    }).build();*/

    //cloudletsTableBuilder.build()


    printCost(i = 0, broker0, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    printCost(i = 0, brokerFaas, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    println("Total number of finished cloudlets: " + (finishedCloudlets.size()))

    val c: Cloudlet = (finishedCloudlets.stream().max(Comparator.comparing((c: Cloudlet) => c.getActualCpuTime)).get())
    println("Max exec time: " + (c.getActualCpuTime))
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


    val random: ContinuousDistribution = new UniformDistr();
    val datacenter: NetworkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicyBestFit()) // new VmAllocationPolicyRandom(random)

    datacenter
      .getCharacteristics()
      .setCostPerSecond(config.getDouble("datacenter.costPerSecond"))
      .setCostPerMem(config.getDouble("datacenter.costPerMem"))
      .setCostPerStorage(config.getDouble("datacenter.costPerStorage"))
      .setCostPerBw(config.getDouble("datacenter.costPerBw"));

    datacenter.setSchedulingInterval(config.getInt("datacenter.schedulingInterval"))
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
      .setVmScheduler(new VmSchedulerSpaceShared())// timeshared/spaceshared?
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
    val numOfStoredFiles = config.getInt("datacenter.numOfStoredFiles")
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
    val numOfStandardVMs = config.getInt("vm.standardVms.num")
    //val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations

    (1 to numOfStandardVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.PEs"))).toList
    //val faasVms = (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs")).setCloudletScheduler(new CloudletSchedulerSpaceShared())).toList

    //standardVms ::: faasVms
  }

  def createFaasVms(): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations
    (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs"))).toList
  }

  def createCloudlets(): List[IaasPaasFaasCloudlet] = {
    val numOfIaasCloudlets = config.getInt("cloudlet.iaas.num")
    val numOfPaasCloudlets = config.getInt("cloudlet.paas.num")
    //val numOfFaasCloudlets = config.getInt("cloudlet.faas.num")

    val iaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfIaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.IAAS)).toList
    val paasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfPaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.PAAS)).toList
    //val faasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfFaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList

    iaasCloudlets.foreach(c => c.setupComputingResources())
    paasCloudlets.foreach(c => c.setupComputingResources())
    //faasCloudlets.foreach(c => c.setupComputingResources())

    iaasCloudlets ::: paasCloudlets
      //::: faasCloudlets
  }

  def createFaasCloudlets(): List[IaasPaasFaasCloudlet] = {
    val numOfFaasCloudlets = config.getInt("cloudlet.faas.num")
    val faasCloudlets: List[IaasPaasFaasCloudlet] = (1 to numOfFaasCloudlets).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList
    faasCloudlets.foreach(c => c.setupComputingResources())
    faasCloudlets
  }

  def periodicEventHandler(eventInfo: EventInfo) = {
    val time = eventInfo.getTime()
    if(time <= config.getInt("newCloudletsDuration")) {
      val howManyNewCloudlets = config.getInt("howManyNewCloudlets")
      val newIaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.IAAS)).toList
      val newPaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.PAAS)).toList
      val newFaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList

      val newCloudlets = newIaasCloudlets ::: newPaasCloudlets //::: newFaasCloudlets
      newCloudlets.foreach(c => c.setupComputingResources());
      newFaasCloudlets.foreach(c => c.setupComputingResources())



      broker0.submitCloudletList(newCloudlets.asJava);
      brokerFaas.submitCloudletList(newFaasCloudlets.asJava)
    }
  }

  def setupFileRequirementsForCloudlets(cloudletList: List[IaasPaasFaasCloudlet]): Unit = {
    val numOfStoredFiles = config.getInt("datacenter.numOfStoredFiles")
    val range = 1 to numOfStoredFiles

    range.foreach(i => cloudletList(i).addRequiredFile(s"file$i.txt"));
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

