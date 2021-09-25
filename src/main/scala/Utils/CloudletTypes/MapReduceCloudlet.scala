package Utils.CloudletTypes

import Simulations.MapReduce.MapReduceSimulation.config
import org.cloudbus.cloudsim.cloudlets.network.NetworkCloudlet
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}

enum TypeOfMapReduceTask:
  case MAPPER, REDUCER

class MapReduceCloudlet(val typeOfMapReduceTask: TypeOfMapReduceTask)
  extends NetworkCloudlet(
    config.getInt("cloudlet.defaultLength"),
    config.getInt("cloudlet.defaultPEs"),
  ) { // new UtilizationModelDynamic(config.getDouble("utilizationRatio"))

  def getTypeOfMapReduceTaskText(): String = {
    typeOfMapReduceTask match {
      case TypeOfMapReduceTask.MAPPER => "MAPPER"
      case TypeOfMapReduceTask.REDUCER => "REDUCER"
    }
  }

}
