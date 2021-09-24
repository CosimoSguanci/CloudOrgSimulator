package Utils

import com.typesafe.config.Config
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyAbstract, VmAllocationPolicyBestFit, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerAbstract, CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerAbstract, VmSchedulerSpaceShared, VmSchedulerTimeShared}

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

  def createDatacenterStorage(config: Config, numOfDatacenters: Int): List[DatacenterStorage] = {
    val numOfSanForDatacenter = config.getInt("datacenter.numOfSanForDatacenter")
    val sanStorageCapacity = config.getLong("datacenter.sanStorageCapacityForDatacenter")
    val sanBW = config.getDouble("datacenter.sanBWInMbps")
    val sanNetworkLatency = config.getDouble("datacenter.sanNetworkLatencySec")

    val range = 1 to numOfDatacenters

    range.map(i => {
      val sanRange = 1 to numOfSanForDatacenter
      val sanList: List[SanStorage] = sanRange.map(j => new SanStorage(sanStorageCapacity, sanBW, sanNetworkLatency)).toList
      sanList.foreach(san => addFilesToSan(config, san));
      new DatacenterStorage(sanList.asJava)
    }).toList
  }

  def addFilesToSan(config: Config, san: SanStorage): Unit = {
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
}
