package Simulations.IaaS_PaaS_FaaS

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.*
import Utils.CloudletTypes.*
import Utils.PolicyEnums.{CloudletSchedulerType, UtilizationModelType, VmAllocationType, VmSchedulerType}
import com.typesafe.config.{Config, ConfigFactory}
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
import org.cloudbus.cloudsim.provisioners.{ResourceProvisioner, ResourceProvisionerSimple}
import org.cloudbus.cloudsim.resources.*
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerSpaceShared, CloudletSchedulerTimeShared}
import org.cloudbus.cloudsim.schedulers.vm.{VmSchedulerSpaceShared, VmSchedulerTimeShared}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.cloudbus.cloudsim.vms.{Vm, VmCost, VmSimple}
import org.cloudsimplus.autoscaling.VmScaling
import org.cloudsimplus.builders.tables.{CloudletsTableBuilder, TextTableColumn}
import org.cloudsimplus.listeners.EventInfo

import java.util.Comparator
import scala.::
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

/**
 * The most basic IaaS / PaaS / FaaS simulation, that resembles a set of AWS services that are offered to customers (e.g. respectively EC2, Elastic Beanstalk and Lambda).
 * Here the focus is mostly on testing autoscaling policies and different types of cloudlets and VMs for different types of services. As a matter of fact,
 * FaaS tasks are lighter w.r.t. IaaS and PaaS, and therefore they should be allocated to specific lightweight VMs. This has been achieved through the user of a FaaS broker.
 */
class IaasPaasFaasSimulationBasic

object IaasPaasFaasSimulationBasic:

  val CONFIG = "iaasPaasFaasSimulationBasic";

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
    CommonMethods.setupFileRequirementsForCloudletsIaaSPaaSFaaS(config, cloudletList)

    logger.info("All files to some Cloudlets to be requested")

    // A periodic listener is created, it is called every time the simulation clock advances
    simulation.addOnClockTickListener(periodicEventHandler)

    brokerIaasPaaS.submitVmList(vmList.asJava)
    brokerIaasPaaS.submitCloudletList(cloudletList.asJava)

    brokerFaas.submitVmList(faasVmList.asJava)
    brokerFaas.submitCloudletList(faasCloudletList.asJava)

    logger.info("VMs and Cloudlets submitted to the broker")
    logger.info("The simulation is about to start...")
    simulation.start();

    val finishedCloudlets = brokerIaasPaaS.getCloudletFinishedList()

    finishedCloudlets.addAll(brokerFaas.getCloudletFinishedList())

    val cloudletsTableBuilder: CloudletsTableBuilder = new CloudletsTableBuilder(finishedCloudlets);

    //cloudletsTableBuilder.build()

    CommonMethods.printCost(i = 0, brokerIaasPaaS, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    CommonMethods.printCost(i = 0, brokerFaas, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
    println()
    println("Total number of finished cloudlets: " + (finishedCloudlets.size()))

    val c: Cloudlet = (finishedCloudlets.stream().max(Comparator.comparing((c: Cloudlet) => c.getActualCpuTime)).get())
    println("Max exec time: " + (c.getActualCpuTime))
  }

  /**
   * The listener function that is fired every time the simulation clock advances. In this case, every time this function is called, a certain (configurable)
   * number of cloudlets are added to the simulation and submitted to brokers.
   *
   * @param eventInfo
   * @return
   */
  def periodicEventHandler(eventInfo: EventInfo) = {
    val time = eventInfo.getTime()
    if (time <= config.getInt("newCloudletsDuration")) {
      val howManyNewCloudlets = config.getInt("howManyNewCloudlets")
      val newIaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.IAAS)).toList
      val newPaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.PAAS)).toList
      val newFaasCloudlets: List[IaasPaasFaasCloudlet] = (1 to (howManyNewCloudlets / 3)).map(i => new IaasPaasFaasCloudlet(DeploymentModel.FAAS)).toList

      val newCloudlets = newIaasCloudlets ::: newPaasCloudlets

      // Different computing resources for IaaS/PaaS vs FaaS
      newCloudlets.foreach(c => c.setupComputingResources())
      newFaasCloudlets.foreach(c => c.setupComputingResources())

      brokerIaasPaaS.submitCloudletList(newCloudlets.asJava)
      brokerFaas.submitCloudletList(newFaasCloudlets.asJava)

      logger.info(s"$howManyNewCloudlets new cloudlets dynamically created and submitted to brokers")
    }
  }

