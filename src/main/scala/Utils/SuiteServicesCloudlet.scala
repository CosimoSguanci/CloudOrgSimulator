package Utils

import Simulations.SimulationSuiteServices.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic

enum TypeOfService:
  case EMAIL, CLOUD_DOCS, CLOUD_STORAGE

class SuiteServicesCloudlet(val typeOfService: TypeOfService)
  extends CloudletSimple(
    config.getInt("saasSimulation.cloudlet.length"),
    config.getInt("saasSimulation.cloudlet.PEs"),
    new UtilizationModelDynamic(config.getDouble("saasSimulation.utilizationRatio"))) {
  
  def getTypeOfServiceText(): String = {
    typeOfService match {
      case TypeOfService.EMAIL => "Email"
      case TypeOfService.CLOUD_DOCS => "Cloud Docs"
      case TypeOfService.CLOUD_STORAGE => "Cloud Storage"
    }
  }

}
