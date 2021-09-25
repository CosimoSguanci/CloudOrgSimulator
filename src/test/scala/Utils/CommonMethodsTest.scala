package Utils

import Simulations.SaaS.SaasWorkspaceSimulationBasic.config
import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic.config as config2
import Utils.CloudletTypes.{DeploymentModel, TypeOfService}
import Utils.PolicyEnums.{CloudletSchedulerType, UtilizationModelType, VmAllocationType, VmSchedulerType}
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.hosts.HostSimple
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommonMethodsTest extends AnyFlatSpec with Matchers {
  behavior of "CommonMethods"

  it should "correctly create datacenters" in {
    val simulation = new CloudSim()
    val datacenters = CommonMethods.createDatacenters(config, simulation, List(), config.getInt("datacenter.num"), VmAllocationType.VM_ALLOCATION_SIMPLE, VmSchedulerType.VM_SCHEDULER_TIME_SHARED)

    datacenters.length shouldBe config.getInt("datacenter.num")

    datacenters.foreach(datacenter => {
      datacenter.getVmAllocationPolicy shouldBe a[VmAllocationPolicySimple]

      datacenter.getCharacteristics.getCostPerSecond shouldBe config.getDouble("datacenter.costPerSecond")
      datacenter.getCharacteristics.getCostPerMem shouldBe config.getDouble("datacenter.costPerMem")
      datacenter.getCharacteristics.getCostPerBw shouldBe config.getDouble("datacenter.costPerBw")
      datacenter.getCharacteristics.getCostPerStorage shouldBe config.getDouble("datacenter.costPerStorage")

      datacenter.getHostList().forEach(host => {
        host.getVmScheduler shouldBe a[VmSchedulerTimeShared]
        host.getMips shouldBe config.getDouble("host.mipsCapacityPE")
        host.getRam.getAvailableResource shouldBe config.getLong("host.RAMInMBs")
        host.getBw.getAvailableResource shouldBe config.getLong("host.BandwidthInMBps")
        host.getStorage.getAvailableResource shouldBe config.getLong("host.StorageInMBs")
        host.getPeList.size() shouldBe config.getInt("host.PEs")
      })
    })
  }

  it should "correctly create datacenter storage" in {
    val simulation = new CloudSim()
    val datacenterStorage = CommonMethods.createDatacenterStorage(config, config.getInt("datacenter.num"), config.getInt("cloudlet.numStorageCloudlets"))

    datacenterStorage.length shouldBe config.getInt("datacenter.num")

    datacenterStorage.foreach(ds => {
      ds.getStorageList.size() shouldBe config.getInt("datacenter.numOfSanForDatacenter")
      ds.getStorageList.forEach(san => {
        san.getBandwidth shouldBe config.getDouble("datacenter.sanBWInMbps")
        san.getNetworkLatency shouldBe config.getDouble("datacenter.sanNetworkLatencySec")
        san.getFileList.forEach(file => {
          file.getSize should (be(config.getInt("datacenter.sanFileSizeInMB_small")) or be(config.getInt("datacenter.sanFileSizeInMB_medium")) or be(config.getInt("datacenter.sanFileSizeInMB_big")))
        })
      })
    })
  }

  it should "correctly create VMs for SaaS" in {
    val vmList = CommonMethods.createVmsSaaS(config, CloudletSchedulerType.CLOUDLET_SCHEDULER_SPACE_SHARED)

    vmList.length shouldBe config.getInt("vm.num")

    vmList.foreach(vm => {
      vm.getCloudletScheduler shouldBe a[CloudletSchedulerSpaceShared]
    })
  }

  it should "correctly create Cloudlets for SaaS" in {
    val cloudletList = CommonMethods.createCloudletsSaaS(config)

    cloudletList.filter(c => c.typeOfService == TypeOfService.EMAIL).length shouldBe config.getInt("cloudlet.numEmailCloudlets")
    cloudletList.filter(c => c.typeOfService == TypeOfService.CLOUD_DOCS).length shouldBe config.getInt("cloudlet.numDocsCloudlets")
    cloudletList.filter(c => c.typeOfService == TypeOfService.CLOUD_STORAGE).length shouldBe config.getInt("cloudlet.numStorageCloudlets")

    cloudletList.foreach(c => {
      c.getLength shouldBe config.getLong("cloudlet.length")
      c.getNumberOfPes shouldBe config.getLong("cloudlet.PEs")
    })
  }

  it should "correctly setup file requirements for SaaS" in {
    val cloudletList = CommonMethods.createCloudletsSaaS(config)
    CommonMethods.setupFileRequirementsForCloudletsSaaS(config, cloudletList)

    val cloduletStorage = cloudletList.filter(c => c.typeOfService == TypeOfService.CLOUD_STORAGE)

    cloduletStorage.lazyZip(0 to (cloduletStorage.length - 1)).foreach {
      (c, i) => {
        c.getRequiredFiles.size() shouldBe 1
      }
    }
  }

  it should "correctly create VMs for IaaS/PaaS" in {
    val vmList = CommonMethods.createVmsIaaSPaaS(config2)

    vmList.length shouldBe config2.getInt("vm.standardVms.num")
  }

  it should "correctly create Cloudlets for IaaS/PaaS" in {
    val cloudletList = CommonMethods.createCloudletsIaaSPaaS(config2, UtilizationModelType.FULL)

    cloudletList.filter(c => c.deploymentModel == DeploymentModel.IAAS).length shouldBe config2.getInt("cloudlet.iaas.num")
    cloudletList.filter(c => c.deploymentModel == DeploymentModel.PAAS).length shouldBe config2.getInt("cloudlet.paas.num")
  }

  it should "correctly create VMs for FaaS" in {
    val vmList = CommonMethods.createVmsFaaS(config2)

    vmList.length shouldBe config2.getInt("vm.faasVms.num")
  }

  it should "correctly create Cloudlets for FaaS" in {
    val cloudletList = CommonMethods.createCloudletsFaaS(config2)

    cloudletList.length shouldBe config2.getInt("cloudlet.faas.num")
  }

}
