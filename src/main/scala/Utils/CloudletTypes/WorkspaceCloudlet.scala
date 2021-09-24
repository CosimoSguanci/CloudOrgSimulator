package Utils.CloudletTypes

import Simulations.SaaS.SaasWorkspaceSimulationBasic.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull}

/**
 * Enum used to specify the type of service that the cloudlet is offering to users
 */
enum TypeOfService:
  case EMAIL, CLOUD_DOCS, CLOUD_STORAGE

/**
 * Represents a Cloudlet to be executed in Workspace simulations (SaaS deployment model)
 * 
 * @param typeOfService the service that this cloudlet is offering to users
 */
class WorkspaceCloudlet(val typeOfService: TypeOfService)
  extends CloudletSimple(
    config.getInt("cloudlet.defaultLength"),
    config.getInt("cloudlet.defaultPEs")) {

  /**
   * Convert the TypeOfService enum to String, used to print results
   * 
   * @return the string representation of the specific service
   */
  def getTypeOfServiceText(): String = {
    typeOfService match {
      case TypeOfService.EMAIL => "Email"
      case TypeOfService.CLOUD_DOCS => "Cloud Docs"
      case TypeOfService.CLOUD_STORAGE => "Cloud Storage"
    }
  }

}
