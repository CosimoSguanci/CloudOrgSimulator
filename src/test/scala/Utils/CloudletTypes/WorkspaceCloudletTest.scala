package Utils.CloudletTypes

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WorkspaceCloudletTest extends AnyFlatSpec with Matchers {
  behavior of "WorkspaceCloudlet"
  
  it should "correctly convert the TypeOfService enum to String" in {
    val c1 = new WorkspaceCloudlet(TypeOfService.EMAIL)
    c1.getTypeOfServiceText() shouldBe "Email"

    val c2 = new WorkspaceCloudlet(TypeOfService.CLOUD_DOCS)
    c2.getTypeOfServiceText() shouldBe "Cloud Docs"

    val c3 = new WorkspaceCloudlet(TypeOfService.CLOUD_STORAGE)
    c3.getTypeOfServiceText() shouldBe "Cloud Storage"
  }
}