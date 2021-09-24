package Simulations
import Simulations.SaaS.SaasWorkspaceSimulationVmSchedulerTimeShared
import Simulations.SaaS.SaasWorkspaceSimulationVmSchedulerTimeShared.config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.cloudbus.cloudsim.schedulers.cloudlet.{CloudletSchedulerTimeShared}

class SaasWorkspaceSimulationVmSchedulerTimeSharedTest extends AnyFlatSpec with Matchers {
  behavior of "SaasWorkspaceSimulationVmSchedulerTimeShared"

  it should "correctly create VMs" in {
    val vmNum = config.getInt("vm.num")
    val vmList = SaasWorkspaceSimulationVmSchedulerTimeShared.createVms()
    val vmPEs = config.getLong("vm.PEs")
    val vmMips = config.getDouble("vm.vmMips")

    vmList.length shouldBe vmNum

    vmList.foreach(vm => {
      vm.getNumberOfPes shouldBe vmPEs
      vm.getMips shouldBe vmMips
      vm.getCloudletScheduler shouldBe a [CloudletSchedulerTimeShared]
    })
  }
}
