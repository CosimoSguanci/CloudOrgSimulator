import HelperUtils.CreateLogger
import Simulations.SimulationSuiteServices

object SimulationSuiteServicesRun:
  val logger = CreateLogger(classOf[SimulationSuiteServicesRun])

  @main def runSimulationSuiteServices =
    logger.info("Constructing a cloud model...")
    SimulationSuiteServices.Start()
    logger.info("Finished cloud simulation...")

class SimulationSuiteServicesRun
