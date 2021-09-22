package Utils

import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.{UtilizationModelDynamic, UtilizationModelFull, UtilizationModelStochastic}

enum DeploymentModel:
  case IAAS, PAAS, FAAS, NOT_SPECIFIED

class IaasPaasFaasCloudlet(val deploymentModel: DeploymentModel)
  extends CloudletSimple(
    config.getInt("cloudlet.defaultLength"),
    config.getInt("cloudlet.defaultPEs"),
    new UtilizationModelFull()) { // new UtilizationModelDynamic(config.getDouble("utilizationRatio"))

  
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


  def getDeploymentModelText(): String = {
    deploymentModel match {
      case DeploymentModel.IAAS => "Iaas"
      case DeploymentModel.PAAS => "PaaS"
      case DeploymentModel.FAAS => "FaaS"
      case _ => "Not specified"
    }
  }

}
