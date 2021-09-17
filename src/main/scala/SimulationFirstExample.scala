import HelperUtils.CreateLogger
import Simulations.{BasicFirstExampleSimulation}

object SimulationFirstExample:
  val logger = CreateLogger(classOf[Simulation])

  @main def runSimulationFirstExample =
    logger.info("Constructing a cloud model...")
    BasicFirstExampleSimulation.Start()
    logger.info("Finished cloud simulation...")

class SimulationFirstExample
