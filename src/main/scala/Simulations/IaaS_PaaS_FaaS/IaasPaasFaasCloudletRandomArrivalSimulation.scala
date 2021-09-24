package Simulations.IaaS_PaaS_FaaS

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{CommonMethods, DeploymentModel, IaasPaasFaasCloudlet, ScalingStrategy, TypeOfService, UtilizationModelType, VmAllocationType, VmSchedulerType, VmWithScalingFactory}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyBestFit, VmAllocationPolicyRandom, VmAllocationPolicyRoundRobin, VmAllocationPolicySimple}
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
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}

import java.util.Comparator
import scala.::
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

/**
 * The second IaaS / PaaS / FaaS simulation, that tries to model a more realistic behaviour regarding new cloudlets. Every time the simulation clock
 * advances, new cloudlets are created with a certain (configurable) probability.
 */
class  IaasPaasFaasCloudletRandomArrivalSimulation

object IaasPaasFaasCloudletRandomArrivalSimulation:
  val CONFIG = "iaasPaasFaasSimulationCloudletRandomArrival"

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[IaasPaasFaasSimulationBasic])

  val vmList: List[Vm] = CommonMethods.createVmsIaaSPaaS(config)

  // Here UtilizationModelType.FULL is used, to simulate the stochastic begaviour described in the documentation, it is enough to change it to UtilizationModelType.STOCHASTIC
  val cloudletList: List[IaasPaasFaasCloudlet] = CommonMethods.createCloudletsIaaSPaaS(config, UtilizationModelType.FULL)

  val faasVmList: List[Vm] = CommonMethods.createVmsFaaS(config)
  val faasCloudletList: List[IaasPaasFaasCloudlet] = CommonMethods.createCloudletsFaaS(config)

  val simulation = new CloudSim()
  val brokerIaasPaaS = new DatacenterBrokerSimple(simulation)
  val brokerFaas = new DatacenterBrokerSimple(simulation)

  val random: ContinuousDistribution = new UniformDistr()

  def Start() = {

    val numOfDatacenters = config.getInt("datacenter.num")

    val datacenters: List[Datacenter] = CommonMethods.createDatacenters(config, simulation, List.empty, numOfDatacenters, VmAllocationType.VM_ALLOCATION_SIMPLE, VmSchedulerType.VM_SCHEDULER_SPACE_SHARED)
    logger.info(s"$numOfDatacenters Datacenters created successfully")

    val datacenterStorageList: List[DatacenterStorage] = CommonMethods.createDatacenterStorage(config, numOfDatacenters, config.getInt("datacenter.numOfStoredFiles"));

    datacenters.foreach(d => d.setSchedulingInterval(config.getInt("datacenter.schedulingInterval")))

    // We assign datacenter storage to the corresponding datacenter and vice versa
    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }

    logger.info("Datacenters Storage created and assigned to datacenters")

    /**
     * Call to the method that creates the datacenter storage together with SANs and associated files,
     * that will be then required by a subset of the total number of cloudlets in the system
     */
    setupFileRequirementsForCloudlets(cloudletList)

    logger.info("All files to some Cloudlets to be requested")

    // A periodic listener is created, it is called every time the simulation clock advances
    simulation.addOnClockTickListener(periodicEventHandler)

    brokerIaasPaaS.submitVmList(vmList.asJava)
    brokerIaasPaaS.submitCloudletList(cloudletList.asJava)

    brokerFaas.submitVmList(faasVmList.asJava)
    brokerFaas.submitCloudletList(faasCloudletList.asJava)

    logger.info("VMs and Cloudlets submitted to the broker")

    simulation.terminateAt(config.getDouble("simulationDuration"))
    simulation.addOnClockTickListener(periodicEventHandler);

    logger.info("The simulation is about to start...")
    simulation.start();

    val finishedCloudlets = brokerIaasPaaS.getCloudletFinishedList()

    finishedCloudlets.addAll(brokerFaas.getCloudletFinishedList())

    val cloudletsTableBuilder: CloudletsTableBuilder = new CloudletsTableBuilder(finishedCloudlets);

    //cloudletsTableBuilder.build()

    printCost(i = 0, brokerIaasPaaS, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    printCost(i = 0, brokerFaas, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    println("Total number of finished cloudlets: " + (finishedCloudlets.size()))

    val c: Cloudlet = (finishedCloudlets.stream().max(Comparator.comparing((c: Cloudlet) => c.getActualCpuTime)).get())
    println("Max exec time: " + (c.getActualCpuTime))
  }

  /**
   * The listener function that is fired every time the simulation clock advances. In this case, every time this function is called, with a fixed (configurable) probability
   * some cloudlets are added to the simulation and submitted to brokers.
   * 
   * @param eventInfo
   * @return
   */
  def periodicEventHandler(eventInfo: EventInfo) = {
    val time = eventInfo.getTime()
    if(random.sample() < config.getDouble("newCloudletsProbability")) {
      val howManyNewCloudlets = scala.util.Random.nextInt(config.getInt("howManyNewCloudlets"))
      val newIaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.IAAS)).toList
      val newPaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.PAAS)).toList
      val newFaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList

      val newCloudlets = newIaasCloudlets ::: newPaasCloudlets
      newCloudlets.foreach(c => c.setupComputingResources())
      newFaasCloudlets.foreach(c => c.setupComputingResources())

      brokerIaasPaaS.submitCloudletList(newCloudlets.asJava)
      brokerFaas.submitCloudletList(newFaasCloudlets.asJava)
    }
  }

  /**
   * Models the realistic behaviour of some cloudlets requiring storage to the datacenter
   *
   * @param cloudletList The list of created cloudlets
   */
  def setupFileRequirementsForCloudlets(cloudletList: List[IaasPaasFaasCloudlet]): Unit = {
    val numOfStoredFiles = config.getInt("datacenter.numOfStoredFiles")
    val range = 1 to numOfStoredFiles

    range.foreach(i => cloudletList(i).addRequiredFile(s"file$i.txt"));
  }

  /**
   * Print total costs for running the system. In this case is used to print both the costs for IaaS/PaaS and FaaS
   *
   * @param i                   index for the recursion
   * @param broker              the broker to be considered to compute costs
   * @param totalCost           partial total cost
   * @param processingTotalCost partial total processing cost
   * @param memoryTotalCost     partial total memory cost
   * @param storageTotalCost    partial total storage cost
   * @param bwTotalCost         partial total bandwidth cost
   * @param totalNonIdleVms     partial total number of created VMs that weren't idle during the simulation
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

      return
    }

    val vm: Vm = broker.getVmCreatedList().get(i)
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

