package Simulations

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{ScalingStrategy, VmWithScalingFactory}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyRandom, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.distributions.{ContinuousDistribution, UniformDistr}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.CloudletsTableBuilder

import scala.::
import scala.jdk.CollectionConverters.*

class SimulationSuiteServices

object SimulationSuiteServices:
  val config = ObtainConfigReference("simulationSuiteServices") match {
    case Some(value) => value
    case None => throw new RuntimeException("Cannot obtain a reference to the config data.")
  }
  val logger = CreateLogger(classOf[SimulationSuiteServices])

  def Start() = {
    val simulation = new CloudSim()
    //simulation.terminateAt(config.getDouble("simulationSuiteServices.duration"))
    val numOfDatacenters = config.getInt("simulationSuiteServices.datacenter.num")

    val datacenters: List[Datacenter] = createDatacenters(simulation, List.empty, numOfDatacenters)
    val datacenterStorageList: List[DatacenterStorage] = createDatacenterStorage(numOfDatacenters);

    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }

    val vmList: List[Vm] = createVms()
    val cloudletlist: List[Cloudlet] = createCloudlets()

    setupFileRequirementsForCloudlets(cloudletlist);

    val broker0 = new DatacenterBrokerSimple(simulation)
    broker0.submitVmList(vmList.asJava)
    broker0.submitCloudletList(cloudletlist.asJava)

    simulation.start();

    val finishedCloudlets = broker0.getCloudletFinishedList()
    new CloudletsTableBuilder(finishedCloudlets).build()

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
    val numOfHosts = config.getInt("simulationSuiteServices.host.num")
    val range = 1 to numOfHosts
    val hostList: List[Host] = range.map(i => createHost()).toList

    //.setSchedulingInterval();


    val random: ContinuousDistribution = new UniformDistr();
    val datacenter: NetworkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicyRandom(random));

    datacenter
      .getCharacteristics()
      .setCostPerSecond(config.getDouble("simulationSuiteServices.datacenter.costPerSecond"))
      .setCostPerMem(config.getDouble("simulationSuiteServices.datacenter.costPerMem"))
      .setCostPerStorage(config.getDouble("simulationSuiteServices.datacenter.costPerStorage"))
      .setCostPerBw(config.getDouble("simulationSuiteServices.datacenter.costPerBw"));

    return datacenter
  }

  def createHost(): Host = {
    val numOfHostPEs = config.getInt("simulationSuiteServices.host.PEs")
    val range = 1 to numOfHostPEs
    val peList: List[Pe] = range.map(i => new PeSimple(config.getInt("simulationSuiteServices.host.mipsCapacity"))).toList

    val hostRam = config.getLong("simulationSuiteServices.host.RAMInMBs")
    val hostBw = config.getLong("simulationSuiteServices.host.BandwidthInMBps")
    val hostStorage = config.getLong("simulationSuiteServices.host.StorageInMBs")
    new HostSimple(hostRam, hostBw, hostStorage, peList.asJava)
  }

  def createDatacenterStorage(numOfDatacenters: Int): List[DatacenterStorage] = {
    val numOfSanForDatacenter = config.getInt("simulationSuiteServices.datacenter.numOfSanForDatacenter")
    val sanStorageCapacity = config.getLong("simulationSuiteServices.datacenter.sanStorageCapacityForDatacenter")
    val sanBW = config.getDouble("simulationSuiteServices.datacenter.sanBWInMBps")
    val sanNetworkLatency = config.getDouble("simulationSuiteServices.datacenter.sanNetworkLatencySec")

    val range = 1 to numOfDatacenters

    range.map(i => {
      val sanRange = 1 to numOfSanForDatacenter
      val sanList: List[SanStorage] = sanRange.map(j => new SanStorage(sanStorageCapacity, sanBW, sanNetworkLatency)).toList
      sanList.foreach(san => addFilesToSan(san));
      new DatacenterStorage(sanList.asJava)
    }).toList
  }

  def addFilesToSan(san: SanStorage): Unit = {
    val numOfStoredFiles = config.getInt("simulationSuiteServices.datacenter.numOfStoredFiles")
    val sizeSmall = config.getInt("simulationSuiteServices.datacenter.sanFileSizeInMB_small")
    val sizeMedium = config.getInt("simulationSuiteServices.datacenter.sanFileSizeInMB_medium")
    val sizeBig = config.getInt("simulationSuiteServices.datacenter.sanFileSizeInMB_big")

    val range = 1 to numOfStoredFiles

    range.foreach(i => {
      val size = if (i < (numOfStoredFiles / 3)) then sizeSmall else if (i < 2 * (numOfStoredFiles / 3)) then sizeMedium else sizeBig
      val file = new File(s"file$i.txt", size);
      san.addFile(file);
    })
  }

  def createVms(): List[Vm] = {
    val scalingStrategyId = config.getInt("simulationSuiteServices.vm.scalingStrategy");
    val numOfVMs = config.getInt("simulationSuiteServices.vm.num")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(scalingStrategyId)).toList
  }

  def createCloudlets(): List[Cloudlet] = {
    val numOfCloudlets = config.getInt("simulationSuiteServices.cloudlet.num")
    val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(config.getDouble("simulationSuiteServices.utilizationRatio"))
    val cloudletLength = config.getInt("simulationSuiteServices.cloudlet.length")
    val cloudletPEs = config.getInt("simulationSuiteServices.cloudlet.PEs")
    (1 to numOfCloudlets).map(i => new CloudletSimple(cloudletLength, cloudletPEs, utilizationModel)).toList
  }

  def setupFileRequirementsForCloudlets(cloudletList: List[Cloudlet]): Unit = {
    val numOfStoredFiles = config.getInt("simulationSuiteServices.numOfStoredFiles")
    val range = 1 to numOfStoredFiles

    cloudletList.lazyZip(range).map {
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
        " created VMs from " + broker.getVmsNumber() + ": " +
        processingTotalCost + "$ " +
        memoryTotalCost + "$ " +
        storageTotalCost + "$ " +
        bwTotalCost + "$ " +
        totalCost + "$")

      return;
    }

    val vm: Vm = broker.getVmCreatedList().get(i);
    val vmCost: VmCost = new VmCost(vm)
    println(vmCost)

    val newTotalCost: Double = totalCost + vmCost.getTotalCost()
    val newProcessingTotalCost: Double = processingTotalCost + vmCost.getProcessingCost()
    val newMemoryTotalCost: Double = memoryTotalCost + vmCost.getMemoryCost()
    val newStorageTotalCost: Double = storageTotalCost + vmCost.getStorageCost()
    val newBwTotalCost: Double = bwTotalCost + vmCost.getBwCost()
    val newTotalNonIdleVms: Int = if (vm.getTotalExecutionTime() > 0) then totalNonIdleVms + 1 else totalNonIdleVms

    printCost(i + 1, broker, newTotalCost, newProcessingTotalCost, newMemoryTotalCost, newStorageTotalCost, newBwTotalCost, newTotalNonIdleVms)
  }

