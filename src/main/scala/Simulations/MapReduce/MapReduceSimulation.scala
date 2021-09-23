package Simulations.MapReduce

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{DeploymentModel, MapReduceCloudlet, ScalingStrategy, TypeOfMapReduceTask, TypeOfService, VmWithScalingFactory}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRandom, VmAllocationPolicyRoundRobin, VmAllocationPolicySimple}
import org.cloudbus.cloudsim.brokers.{DatacenterBrokerBestFit, DatacenterBrokerFirstFit, DatacenterBrokerHeuristic, DatacenterBrokerSimple}
import org.cloudbus.cloudsim.cloudlets.network.{CloudletExecutionTask, CloudletReceiveTask, CloudletSendTask, NetworkCloudlet}
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.datacenters.network.NetworkDatacenter
import org.cloudbus.cloudsim.datacenters.{Datacenter, DatacenterSimple}
import org.cloudbus.cloudsim.distributions.{ContinuousDistribution, UniformDistr}
import org.cloudbus.cloudsim.hosts.network.NetworkHost
import org.cloudbus.cloudsim.hosts.{Host, HostSimple}
import org.cloudbus.cloudsim.network.switches.EdgeSwitch
import org.cloudbus.cloudsim.network.topologies.BriteNetworkTopology
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull}
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.vms.network.NetworkVm

import java.util.Comparator
import scala.::
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class MapReduceSimulation

// MapReduce implemented as a PaaS
object MapReduceSimulation:

  val CONFIG = "mapReduceSimulation";

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[MapReduceSimulation])

  val vmList: List[Vm] = createVms()
  //val cloudletList: List[MapReduceCloudlet] = createCloudlets()
  val mappers: List[MapReduceCloudlet] = createMappers()
  val reducers: List[MapReduceCloudlet] = createReducers()


  //val faasVmList: List[Vm] = createFaasVms()
  //val faasCloudletList: List[MapReduceCloudlet] = createFaasCloudlets()


  val simulation = new CloudSim()
  val masterBroker = new DatacenterBrokerSimple(simulation) // Broker -> Master because assigns tass to workers
  //val brokerFaas = new DatacenterBrokerSimple(simulation)

  def Start() = {

    val numOfDatacenters = config.getInt("datacenter.num")

    val datacenters: List[Datacenter] = createDatacenters(simulation, List.empty, numOfDatacenters)
/*    val datacenterStorageList: List[DatacenterStorage] = createDatacenterStorage(numOfDatacenters);

    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }*/


    //setupFileRequirementsForCloudlets(cloudletList);

    //simulation.addOnClockTickListener(periodicEventHandler);

    //masterBroker.setVmDestructionDelayFunction((vm) => config.getDouble("vm.destructionDelay"))
    //brokerFaas.setVmDestructionDelayFunction((vm) => config.getDouble("vm.destructionDelay"))

    masterBroker.submitVmList(vmList.asJava)
    masterBroker.submitCloudletList(mappers.asJava)
    masterBroker.submitCloudletList(reducers.asJava)

    setupMappersAndReducersTasks()


    //brokerFaas.submitVmList(faasVmList.asJava)
    //brokerFaas.submitCloudletList(faasCloudletList.asJava)


    simulation.start();

    val finishedCloudlets = masterBroker.getCloudletFinishedList()

    //finishedCloudlets.addAll(brokerFaas.getCloudletFinishedList())

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

    cloudletsTableBuilder.build()


    printCost(i = 0, masterBroker, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    //printCost(i = 0, brokerFaas, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    //println()
    println("Total number of finished cloudlets: " + (finishedCloudlets.size()))

    //val c: Cloudlet = (finishedCloudlets.stream().max(Comparator.comparing((c: Cloudlet) => c.getActualCpuTime)).get())
    //println("Max exec time: " + (c.getActualCpuTime))
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
    val hostList: List[NetworkHost] = range.map(i => createHost()).toList

    val datacenter: NetworkDatacenter = new NetworkDatacenter(simulation, hostList.asJava, new VmAllocationPolicySimple()) // new VmAllocationPolicyRandom(random)

    datacenter
      .getCharacteristics()
      .setCostPerSecond(config.getDouble("datacenter.costPerSecond"))
      .setCostPerMem(config.getDouble("datacenter.costPerMem"))
      .setCostPerStorage(config.getDouble("datacenter.costPerStorage"))
      .setCostPerBw(config.getDouble("datacenter.costPerBw"));

    datacenter.setSchedulingInterval(config.getInt("datacenter.schedulingInterval"))
    createNetwork(datacenter)
    return datacenter
  }

  def createNetwork(networkDatacenter: NetworkDatacenter): Unit = {
    val numOfEdgeSwitchesPerDatacenter = config.getInt("datacenter.numEdgeSwitches")
    val edgeSwitches: List[EdgeSwitch] = (1 to numOfEdgeSwitchesPerDatacenter).map(i => new EdgeSwitch(simulation, networkDatacenter)).toList
    edgeSwitches.foreach(e => networkDatacenter.addSwitch(e))

    //networkDatacenter.getHostList().asScala.foreach((host: NetworkHost) => edgeSwitches().connectHost(host))
    val networkHosts = networkDatacenter.getHostList().asScala

    edgeSwitches.lazyZip(0 to (edgeSwitches.length - 1)).map {
      (switch, i) => {
        (0 to (switch.getPorts - 1)).foreach(j => {
          val hostIndex = (switch.getPorts * i) + j
          switch.connectHost(networkHosts(hostIndex).asInstanceOf[NetworkHost])
        })
      }
    }

  }

  def createHost(): NetworkHost = {
    val numOfHostPEs = config.getInt("host.PEs")
    val range = 1 to numOfHostPEs
    val peList: List[Pe] = range.map(i => new PeSimple(config.getInt("host.mipsCapacityPE"))).toList

    val hostRam = config.getLong("host.RAMInMBs")
    val hostBw = config.getLong("host.BandwidthInMBps")
    val hostStorage = config.getLong("host.StorageInMBs")
    val host = new NetworkHost(hostRam, hostBw, hostStorage, peList.asJava) // default vm scheduler: VmSchedulerSpaceShared

    host
      .setRamProvisioner(new ResourceProvisionerSimple())
      .setBwProvisioner(new ResourceProvisionerSimple())

    return host
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
    //val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfVms = config.getInt("vm.num")
    (1 to numOfVms).map(i => new NetworkVm(i,  config.getInt("host.mipsCapacityPE"),  config.getInt("vm.PEs")).setCloudletScheduler(new CloudletSchedulerTimeShared())).toList
    //val numOfFaasVMs = config.getInt("vm.faasVms.num") // specialized lightweight VMs to run serveless computations

    //(1 to numOfStandardVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.PEs"))).toList
    //val faasVms = (1 to numOfFaasVMs).map(i => VmWithScalingFactory(scalingStrategyId, config.getInt("vm.faasVms.PEs")).setCloudletScheduler(new CloudletSchedulerSpaceShared())).toList

    //standardVms ::: faasVms
  }


  def createMappers(): List[MapReduceCloudlet] = {
    val numOfMappers: Int = config.getInt("cloudlet.numOfMappers")
    val mappers: List[MapReduceCloudlet] = (1 to numOfMappers).map(i => new MapReduceCloudlet(TypeOfMapReduceTask.MAPPER)).toList
    (0 to (numOfMappers - 1)).foreach(i => mappers(i).setUtilizationModel(new UtilizationModelFull()).setVm(vmList(i)))
    return mappers
  }

  def createReducers(): List[MapReduceCloudlet] = {
    val numOfReducers: Int = config.getInt("cloudlet.numOfReducers")
    val reducers: List[MapReduceCloudlet] = (1 to numOfReducers).map(i => new MapReduceCloudlet(TypeOfMapReduceTask.REDUCER)).toList
    (0 to (numOfReducers - 1)).foreach(i => reducers(i).setUtilizationModel(new UtilizationModelFull()).setVm(vmList(81)))
    return reducers
  }

  def setupMappersAndReducersTasks(): Unit = {
    // assuming #reducers < #mappers, an equal number of mapper sends outputs to a single reducer
    val mappersForEachReducer: Int = config.getInt("cloudlet.numOfMappers") / config.getInt("cloudlet.numOfReducers")

    // First we model the execution task for the mappers
    mappers.foreach(mapper => {
      val executionTask: CloudletExecutionTask = new CloudletExecutionTask(mapper.getTasks.size(), config.getInt("cloudlet.mapTaskLength"))
      executionTask.setMemory(config.getInt("cloudlet.mapExecMemoryReq"))
      mapper.addTask(executionTask)
    })

    // Then we model the communication between mappers and reducers
    mappers.lazyZip(0 to (mappers.length - 1)).map {
      (mapper, i) => {
        val sendTask: CloudletSendTask = new CloudletSendTask(mapper.getTasks.size())
        sendTask.setMemory(config.getInt("cloudlet.mapSendMemoryReq"))
        mapper.addTask(sendTask)

        val indexOfReducerOfThisMapper: Int = (i / mappersForEachReducer)

        (1 to config.getInt("cloudlet.mapSendNumOfPackets")).foreach(i => sendTask.addPacket(reducers(indexOfReducerOfThisMapper), config.getInt("cloudlet.mapSendPacketLength")))

      }
    }

    reducers.foreach(reducer => {
      val indexOfReducer = reducers.indexOf(reducer)
      (0 to (mappersForEachReducer - 1)).foreach(i => {
        val indexOfSenderMapper = (mappersForEachReducer * indexOfReducer) + i
        val receiveTask: CloudletReceiveTask = new CloudletReceiveTask(reducer.getTasks.size(), mappers(indexOfSenderMapper).getVm)
        receiveTask.setMemory(config.getInt("cloudlet.mapRecvMemoryReq"))
        receiveTask.setExpectedPacketsToReceive(config.getInt("cloudlet.mapSendNumOfPackets"))
        reducer.addTask(receiveTask)
      })
    })

    reducers.foreach(reducer => {
      val executionTask: CloudletExecutionTask = new CloudletExecutionTask(reducer.getTasks.size(), config.getInt("cloudlet.reduceTaskLength"))
      executionTask.setMemory(config.getInt("cloudlet.reduceExecMemoryReq"))
      reducer.addTask(executionTask)
    })
  }

/*  def periodicEventHandler(eventInfo: EventInfo) = {
    val time = eventInfo.getTime()
    if(time <= config.getInt("newCloudletsDuration")) {
      val howManyNewCloudlets = config.getInt("howManyNewCloudlets")
      val newIaasCloudlets: List[MapReduceCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new MapReduceCloudlet(DeploymentModel.IAAS)).toList
      val newPaasCloudlets: List[MapReduceCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new MapReduceCloudlet(DeploymentModel.PAAS)).toList
      val newFaasCloudlets: List[MapReduceCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new MapReduceCloudlet(DeploymentModel.FAAS)).toList

      val newCloudlets = newIaasCloudlets ::: newPaasCloudlets //::: newFaasCloudlets
      newCloudlets.foreach(c => c.setupComputingResources());
      newFaasCloudlets.foreach(c => c.setupComputingResources())



      broker0.submitCloudletList(newCloudlets.asJava);
      brokerFaas.submitCloudletList(newFaasCloudlets.asJava)
    }
  }*/

  def setupFileRequirementsForCloudlets(cloudletList: List[MapReduceCloudlet]): Unit = {
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

