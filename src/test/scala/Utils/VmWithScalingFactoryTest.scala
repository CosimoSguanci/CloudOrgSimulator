package Utils

import org.cloudsimplus.autoscaling.{HorizontalVmScaling, VerticalVmScaling}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VmWithScalingFactoryTest extends AnyFlatSpec with Matchers {
  behavior of "VmWithScalingFactory"

  it should "correctly create VMs with autoscaling" in {
    val vm1 = VmWithScalingFactory(0)

    vm1.getHorizontalScaling should not be (HorizontalVmScaling.NULL)
    vm1.getPeVerticalScaling should be (VerticalVmScaling.NULL)
    vm1.getRamVerticalScaling should be (VerticalVmScaling.NULL)
    vm1.getBwVerticalScaling should be (VerticalVmScaling.NULL)

    val vm2 = VmWithScalingFactory(1)

    vm2.getHorizontalScaling should be (HorizontalVmScaling.NULL)
    vm2.getPeVerticalScaling should not be (VerticalVmScaling.NULL)
    vm2.getRamVerticalScaling should not be (VerticalVmScaling.NULL)
    vm2.getBwVerticalScaling should not be (VerticalVmScaling.NULL)

    val vm3 = VmWithScalingFactory(2)

    vm3.getHorizontalScaling should not be (HorizontalVmScaling.NULL)
    vm3.getPeVerticalScaling should not be (VerticalVmScaling.NULL)
    vm3.getRamVerticalScaling should not be (VerticalVmScaling.NULL)
    vm3.getBwVerticalScaling should not be (VerticalVmScaling.NULL)

    val vm4 = VmWithScalingFactory(3)

    vm4.getHorizontalScaling should be (HorizontalVmScaling.NULL)
    vm4.getPeVerticalScaling should be (VerticalVmScaling.NULL)
    vm4.getRamVerticalScaling should be (VerticalVmScaling.NULL)
    vm4.getBwVerticalScaling should be (VerticalVmScaling.NULL)
  }

}
