package Utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Simulations.SaaS.SaasWorkspaceSimulationBasic.config
import Utils.PolicyEnums.{VmAllocationType, VmSchedulerType}
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple
import org.cloudbus.cloudsim.core.CloudSim
import org.cloudbus.cloudsim.hosts.HostSimple
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared

class CommonMethodsTest extends AnyFlatSpec with Matchers {
  behavior of "CommonMethods"

  it should "correctly create datacenters" in {
    val simulation = new CloudSim()
    val datacenters = CommonMethods.createDatacenters(config, simulation, List(), config.getInt("datacenter.num"), VmAllocationType.VM_ALLOCATION_SIMPLE, VmSchedulerType.VM_SCHEDULER_TIME_SHARED)

    datacenters.length shouldBe config.getInt("datacenter.num")

    datacenters.foreach(datacenter => {
      datacenter.getVmAllocationPolicy shouldBe a [VmAllocationPolicySimple]

      datacenter.getCharacteristics.getCostPerSecond shouldBe config.getDouble("datacenter.costPerSecond")
      datacenter.getCharacteristics.getCostPerMem shouldBe config.getDouble("datacenter.costPerMem")
      datacenter.getCharacteristics.getCostPerBw shouldBe config.getDouble("datacenter.costPerBw")
      datacenter.getCharacteristics.getCostPerStorage shouldBe config.getDouble("datacenter.costPerStorage")

      datacenter.getHostList().forEach(host => {
        host.getVmScheduler shouldBe a [VmSchedulerTimeShared]
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
          file.getSize should (be (config.getInt("datacenter.sanFileSizeInMB_small")) or be (config.getInt("datacenter.sanFileSizeInMB_medium")) or be (config.getInt("datacenter.sanFileSizeInMB_big")))
        })
      })
    })
  }

}
