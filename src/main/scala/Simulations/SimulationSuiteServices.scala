package Simulations

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Simulations.BasicFirstExampleSimulation.config
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.resources.{DatacenterStorage, File, HarddriveStorage, Pe, PeSimple, SanStorage}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.CloudletsTableBuilder
import utils.{ScalingStrategy, VmWithScalingFactory}

import scala.::
import scala.jdk.CollectionConverters.*

class SimulationSuiteServices

object SimulationSuiteServices:
  val config = ObtainConfigReference("simulationSuiteServices") match {
    case Some(value) => value
    case None => throw new RuntimeException("Cannot obtain a reference to the config data.")
  }
  val logger = CreateLogger(classOf[BasicCloudSimPlusExample])

  def Start() = {
    val simulation = new CloudSim()
    //simulation.terminateAt(config.getDouble("simulationSuiteServices.duration"))
    val numOfDatacenters = config.getInt("simulationSuiteServices.numOfDatacenters")

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

    new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicySimple());
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
    val numOfSanForDatacenter = config.getInt("simulationSuiteServices.numOfSanForDatacenter")
    val sanStorageCapacity = config.getLong("simulationSuiteServices.sanStorageCapacityForDatacenter")
    val sanBW = config.getDouble("simulationSuiteServices.sanBWInMBps")
    val sanNetworkLatency = config.getDouble("simulationSuiteServices.sanNetworkLatencySec")

    val range = 1 to numOfDatacenters

    range.map(i => {
      val sanRange = 1 to numOfSanForDatacenter
      val sanList: List[SanStorage] = sanRange.map(j => new SanStorage(sanStorageCapacity, sanBW, sanNetworkLatency )).toList
      sanList.foreach(san => addFilesToSan(san));
      new DatacenterStorage(sanList.asJava)
    }).toList
  }

  def addFilesToSan(san: SanStorage): Unit = {
    val numOfStoredFiles = config.getInt("simulationSuiteServices.numOfStoredFiles")
    val sizeSmall = config.getInt("simulationSuiteServices.sanFileSizeInMB_small")
    val sizeMedium = config.getInt("simulationSuiteServices.sanFileSizeInMB_medium")
    val sizeBig = config.getInt("simulationSuiteServices.sanFileSizeInMB_big")

    val range = 1 to numOfStoredFiles

    range.foreach(i => {
      val size = if(i < (numOfStoredFiles / 3)) then sizeSmall else if(i < 2 * (numOfStoredFiles / 3)) then sizeMedium else sizeBig
      val file = new File(s"file$i.txt", size);
      san.addFile(file);
    })
  }

  def createVms(): List[Vm] = {
    val scalingStrategyId = config.getInt("simulationSuiteServices.vm.scalingStrategy");
    val numOfVMs = config.getInt("simulationSuiteServices.vm.size")
    val hostMips = config.getInt("simulationSuiteServices.host.mipsCapacity")
    val vmPEs = config.getInt("simulationSuiteServices.vm.PEs")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(hostMips, vmPEs, scalingStrategyId)).toList
  }

  def createCloudlets(): List[Cloudlet] = {
    val numOfCloudlets = config.getInt("simulationSuiteServices.cloudlet.size")
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

