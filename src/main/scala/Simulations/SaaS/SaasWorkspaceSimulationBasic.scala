package Simulations.SaaS

import com.typesafe.config.{Config, ConfigFactory}
import HelperUtils.{CreateLogger, ObtainConfigReference}
import Utils.{CloudletSchedulerType, CommonMethods, ScalingStrategy, TypeOfService, VmAllocationType, VmSchedulerType, VmWithScalingFactory, WorkspaceCloudlet}
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

/**
 * The most basic SaaS simulation, whose model is inspired by Google Workspace.
 * It consists of a set of business services deployed on the cloud and offered to organizations as business tools.
 * The services that are offered by the system is represented by the types of cloudlets that can be created: email service, cloud docs, cloud storage.
 */
class SaasWorkspaceSimulationBasic

object SaasWorkspaceSimulationBasic:

  val CONFIG = "saasSimulationWorkspaceBasic"

  val config: Config = ConfigFactory.load(s"$CONFIG.conf")
  val logger = CreateLogger(classOf[SaasWorkspaceSimulationBasic])

  def Start() = {
    val simulation = new CloudSim()

    logger.info("Simulation instantiated")

    val numOfDatacenters = config.getInt("datacenter.num")
    val datacenters: List[Datacenter] = CommonMethods.createDatacenters(config, simulation, List.empty, numOfDatacenters, VmAllocationType.VM_ALLOCATION_SIMPLE, VmSchedulerType.VM_SCHEDULER_SPACE_SHARED)

    logger.info(s"$numOfDatacenters Datacenters created successfully")

    /**
     * Call to the method that creates the datacenter storage together with SANs and associated files,
     * that will be then required by cloudlets that represent cloud storage requests by customers
     */
    val datacenterStorageList: List[DatacenterStorage] = CommonMethods.createDatacenterStorage(config, numOfDatacenters);

    // We assign datacenter storage to the corresponding datacenter and vice versa
    datacenters.lazyZip(datacenterStorageList).foreach {
      (d, ds) => {
        d.setDatacenterStorage(ds)
        ds.setDatacenter(d)
      }
    }

    logger.info("Datacenters Storage created and assigned to datacenters")

    val vmList: List[Vm] = CommonMethods.createVmsSaaS(config, CloudletSchedulerType.CLOUDLET_SCHEDULER_TIME_SHARED)

    logger.info(s"VMs creation completed")

    val cloudletlist: List[WorkspaceCloudlet] = CommonMethods.createCloudletsSaaS(config)

    logger.info(s"Cloudlets creation completed")

    // Call to the method that create requirements for files for all the cloud storage cloudlets
    CommonMethods.setupFileRequirementsForCloudletsSaaS(config, cloudletlist);

    logger.info("All files were assigned to Cloud Storage Cloudlets to be requested")

    val broker0 = new DatacenterBrokerSimple(simulation)

    broker0.submitVmList(vmList.asJava)
    broker0.submitCloudletList(cloudletlist.asJava)

    logger.info("VMs and Cloudlets submitted to the broker")
    logger.info("The simulation is about to start...")
    simulation.start();

    val finishedCloudlets = broker0.getCloudletFinishedList()

    val cloudletsTableBuilder: CloudletsTableBuilder = new CloudletsTableBuilder(finishedCloudlets);

    // A new column is added to know which is the type of cloudlet for every row
    cloudletsTableBuilder.addColumn(1, new TextTableColumn("CloudletType", ""), new java.util.function.Function[Cloudlet, Object] {
      override def apply(c: Cloudlet): Object = {
        cloudletlist.filter(p => p.equals(c)).map(a => a.getTypeOfServiceText())(0)
      }
    }).build();

    CommonMethods.printCost(i = 0, broker0, totalCost = 0, memoryTotalCost = 0, processingTotalCost = 0, storageTotalCost = 0, bwTotalCost = 0, totalNonIdleVms = 0)
  }

