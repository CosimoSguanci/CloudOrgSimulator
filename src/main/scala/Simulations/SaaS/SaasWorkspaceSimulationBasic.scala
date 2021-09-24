package Simulations.SaaS

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{CommonMethods, ScalingStrategy, TypeOfService, VmAllocationType, VmSchedulerType, VmWithScalingFactory, WorkspaceCloudlet}
import org.cloudbus.cloudsim.allocationpolicies.{VmAllocationPolicyRandom, VmAllocationPolicySimple}
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

class SaasWorkspaceSimulationBasic

object SaasWorkspaceSimulationBasic:

  val CONFIG = "saasSimulationWorkspaceBasic"

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[SaasWorkspaceSimulationBasic])

  def Start() = {
    val simulation = new CloudSim()

    val numOfDatacenters = config.getInt("datacenter.num")

    val datacenters: List[Datacenter] = CommonMethods.createDatacenters(config, simulation, List.empty, numOfDatacenters, VmAllocationType.VM_ALLOCATION_SIMPLE, VmSchedulerType.VM_SCHEDULER_SPACE_SHARED)
    val datacenterStorageList: List[DatacenterStorage] = CommonMethods.createDatacenterStorage(config, numOfDatacenters);

    datacenters.lazyZip(datacenterStorageList).map {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }

    val vmList: List[Vm] = createVms()
    val cloudletlist: List[WorkspaceCloudlet] = createCloudlets()

    setupFileRequirementsForCloudlets(cloudletlist);

    val broker0 = new DatacenterBrokerSimple(simulation) // best fit

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

  def createVms(): List[Vm] = {
    val scalingStrategyId = config.getInt("vm.autoscaling.scalingStrategy");
    val numOfVMs = config.getInt("vm.num")
    val range = 1 to numOfVMs
    range.map(i => VmWithScalingFactory(scalingStrategyId).setCloudletScheduler(new CloudletSchedulerTimeShared())).toList
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

  def setupFileRequirementsForCloudlets(cloudletList: List[WorkspaceCloudlet]): Unit = {
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

