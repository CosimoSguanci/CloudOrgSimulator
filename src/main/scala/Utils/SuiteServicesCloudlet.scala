package Utils

import Simulations.SimulationSuiteServices.config
import org.cloudbus.cloudsim.cloudlets.{Cloudlet, CloudletSimple}
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic

enum TypeOfService:
  case EMAIL, CLOUD_DOCS, CLOUD_STORAGE

class SuiteServicesCloudlet(typeOfService: TypeOfService)
  extends CloudletSimple(
    config.getInt("simulationSuiteServices.cloudlet.length"),
    config.getInt("simulationSuiteServices.cloudlet.PEs"),
    new UtilizationModelDynamic(config.getDouble("simulationSuiteServices.utilizationRatio"))) {}
