package Simulations

import HelperUtils.{CreateLogger, ObtainConfigReference}
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.resources.{Pe, PeSimple}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.builders.tables.CloudletsTableBuilder

import scala.jdk.CollectionConverters.*

object BasicFirstExampleSimulation {
  val config = ObtainConfigReference("cloudSimulator2") match {
    case Some(value) => value
    case None => throw new RuntimeException("Cannot obtain a reference to the config data.")
  }
  val logger = CreateLogger(classOf[BasicCloudSimPlusExample])

  def Start() = {
    val simulation = new CloudSim()
    val datacenter0 = createDatacenter(simulation)
    
    val broker0 = new DatacenterBrokerSimple(simulation)
    
    val vmList = createVms()
    val cloudletList = createCloudlets()
    broker0
      .submitVmList(vmList.asJava)
      .submitCloudletList(cloudletList.asJava)
    
    simulation.start();
    
    val finishedCloudlets = broker0.getCloudletFinishedList()
    new CloudletsTableBuilder(finishedCloudlets).build()
  }

  def createDatacenter(simulation: CloudSim): Datacenter = {
    val numOfHosts = config.getInt("cloudSimulator2.host.size")
    val range = 1 to numOfHosts
    val hostList: List[Host] = range.map(i => createHost()).toList
    
    new DatacenterSimple(simulation, hostList.asJava);
  }

  def createHost(): Host = {
    val numOfHostPEs = config.getInt("cloudSimulator2.host.PEs")
    val range = 1 to numOfHostPEs
    val peList: List[Pe] = range.map(i => new PeSimple(config.getInt("cloudSimulator2.host.mipsCapacity"))).toList

    val hostRam = config.getLong("cloudSimulator2.host.RAMInMBs")
    val hostBw = config.getLong("cloudSimulator2.host.BandwidthInMBps")
    val hostStorage = config.getLong("cloudSimulator2.host.StorageInMBs")
    new HostSimple(hostRam, hostBw, hostStorage, peList.asJava)
  }

  def createVms(): List[Vm] = {
    val numOfVMs = config.getInt("cloudSimulator2.vm.size")
    val hostMips = config.getInt("cloudSimulator2.host.mipsCapacity")
    val VmPEs = config.getInt("cloudSimulator2.vm.PEs")
    val range = 1 to numOfVMs
    range.map(i =>
      new VmSimple(hostMips, VmPEs).setSize(1024)).toList
  }

  def createCloudlets(): List[Cloudlet] = {
    val numOfCloudlets = config.getInt("cloudSimulator2.cloudlet.size")
    val utilizationModel: UtilizationModelDynamic = new UtilizationModelDynamic(config.getDouble("cloudSimulator2.utilizationRatio"))
    val cloudletLength = config.getInt("cloudSimulator2.cloudlet.length")
    val cloudletPEs = config.getInt("cloudSimulator2.cloudlet.PEs")
    (1 to numOfCloudlets).map(i => 
      new CloudletSimple(cloudletLength, cloudletPEs, utilizationModel).setSizes(1024)).toList
  }
}
