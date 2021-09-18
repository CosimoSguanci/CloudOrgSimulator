package utils

import HelperUtils.{CreateLogger, ObtainConfigReference}
import Simulations.BasicCloudSimPlusExample
import org.cloudbus.cloudsim.resources.Processor
import org.cloudbus.cloudsim.vms.{Vm, VmSimple}
import org.cloudsimplus.autoscaling.{HorizontalVmScalingSimple, VerticalVmScalingSimple, VmScaling}
import org.cloudsimplus.listeners.VmHostEventInfo

enum ScalingStrategy:
  case HORIZONTAL, VERTICAL, NONE

object VmWithScalingFactory {
  def apply(hostMips: Int, VmPEs: Int, scalingStrategyId: Int): Vm = { // vm storage?
    
    val config = ObtainConfigReference("simulationSuiteServices") match {
      case Some(value) => value
      case None => throw new RuntimeException("Cannot obtain a reference to the config data.")
    }
    val logger = CreateLogger(classOf[BasicCloudSimPlusExample])
    
    val vm = new VmSimple(hostMips, VmPEs);

    val scalingStrategy: ScalingStrategy = mapScalingIdToStrategy(scalingStrategyId);

    scalingStrategy match {
      case ScalingStrategy.HORIZONTAL => vm.setHorizontalScaling(new HorizontalVmScalingSimple())
      case ScalingStrategy.VERTICAL => vm.setPeVerticalScaling(new VerticalVmScalingSimple(classOf[Processor], config.getDouble("simulationSuiteServices.scalingFactor")))// example, to be tuned
      case _ => vm
    }
  }

  def mapScalingIdToStrategy(id: Int): ScalingStrategy = {
    id match {
      case 0 => ScalingStrategy.HORIZONTAL
      case 1 => ScalingStrategy.VERTICAL
      case _ => ScalingStrategy.NONE
    }
  }
}
