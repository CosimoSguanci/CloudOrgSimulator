package Utils.CloudletTypes

import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}

/**
 * Enum used to specify the deployment model of a certain Cloudlet
 */
enum DeploymentModel:
  case IAAS, PAAS, FAAS, NOT_SPECIFIED

/**
 * Represents a Cloudlet to be executed in a Cloud environment that support IaaS, PaaS and FaaS deployment models
 * 
 * @param deploymentModel the deployment model for this cloudlet
 */
class IaasPaasFaasCloudlet(val deploymentModel: DeploymentModel)
  extends CloudletSimple(
    config.getInt("cloudlet.defaultLength"),
    config.getInt("cloudlet.defaultPEs"),
    new UtilizationModelFull()) {

  /**
   * Differentiates computing resources that are needed for different tasks (e.g., FaaS tasks are generally lighter than IaaS tasks)
   */
  def setupComputingResources(): Unit = {
    deploymentModel match {
      case DeploymentModel.IAAS => {
        setLength(config.getLong("cloudlet.iaas.length"))
        setNumberOfPes(config.getLong("cloudlet.iaas.PEs"))
      }
      case DeploymentModel.PAAS => {
        setLength(config.getLong("cloudlet.paas.length"))
        setNumberOfPes(config.getLong("cloudlet.paas.PEs"))
      }
      case DeploymentModel.FAAS => {
        setLength(config.getLong("cloudlet.faas.length"))
        setNumberOfPes(config.getLong("cloudlet.faas.PEs"))
      }
      case _ => {
        setLength(config.getLong("cloudlet.iaas.length"))
        setNumberOfPes(config.getLong("cloudlet.iaas.PEs"))
      }
    }
  }

  /**
   * Convert the DeploymentModel enum to String, used to print results
   * 
   * @return the string representation of the deployment model
   */
  def getDeploymentModelText(): String = {
    deploymentModel match {
      case DeploymentModel.IAAS => "IaaS"
      case DeploymentModel.PAAS => "PaaS"
      case DeploymentModel.FAAS => "FaaS"
      case _ => "Not specified"
    }
  }
}
