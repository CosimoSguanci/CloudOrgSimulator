package Utils

import Simulations.SaaS.SaasWorkspaceSimulationBasic.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull}

enum TypeOfService:
  case EMAIL, CLOUD_DOCS, CLOUD_STORAGE

class SuiteServicesCloudlet(val typeOfService: TypeOfService)
  extends CloudletSimple(
    config.getInt("cloudlet.length"),
    config.getInt("cloudlet.PEs"),
    new UtilizationModelDynamic(config.getDouble("utilizationRatio"))) { // new UtilizationModelDynamic(config.getDouble("saasSimulation.utilizationRatio"))
  
  def getTypeOfServiceText(): String = {
    typeOfService match {
      case TypeOfService.EMAIL => "Email"
      case TypeOfService.CLOUD_DOCS => "Cloud Docs"
      case TypeOfService.CLOUD_STORAGE => "Cloud Storage"
    }
  }

}
