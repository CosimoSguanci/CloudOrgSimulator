package Utils.CloudletTypes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic.config

class IaasPaasFaasCloudletTest extends AnyFlatSpec with Matchers {
  behavior of "IaasPaasFaasCloudlet"

  it should "correctly convert the DeploymentModel enum to String" in {
    val c1 = new IaasPaasFaasCloudlet(DeploymentModel.IAAS)
    c1.getDeploymentModelText() shouldBe "IaaS"

    val c2 = new IaasPaasFaasCloudlet(DeploymentModel.PAAS)
    c2.getDeploymentModelText() shouldBe "PaaS"

    val c3 = new IaasPaasFaasCloudlet(DeploymentModel.FAAS)
    c3.getDeploymentModelText() shouldBe "FaaS"
  }

  it should "correctly setup computing resources" in {
    val c1 = new IaasPaasFaasCloudlet(DeploymentModel.IAAS)
    val c2 = new IaasPaasFaasCloudlet(DeploymentModel.PAAS)
    val c3 = new IaasPaasFaasCloudlet(DeploymentModel.FAAS)

    c1.setupComputingResources()
    c1.getLength shouldBe config.getLong("cloudlet.iaas.length")
    c1.getNumberOfPes shouldBe config.getLong("cloudlet.iaas.PEs")

    c2.setupComputingResources()
    c2.getLength shouldBe config.getLong("cloudlet.paas.length")
    c2.getNumberOfPes shouldBe config.getLong("cloudlet.paas.PEs")

    c3.setupComputingResources()
    c3.getLength shouldBe config.getLong("cloudlet.faas.length")
    c3.getNumberOfPes shouldBe config.getLong("cloudlet.faas.PEs")
  }
}
