import HelperUtils.CreateLogger
import Simulations.{BasicFirstExampleSimulation, SimulationSuiteServices}

object SimulationSuiteServicesRun:
  val logger = CreateLogger(classOf[Simulation])

  @main def runSimulationSuiteServices =
    logger.info("Constructing a cloud model...")
    SimulationSuiteServices.Start()
    logger.info("Finished cloud simulation...")

class SimulationSuiteServicesRun
