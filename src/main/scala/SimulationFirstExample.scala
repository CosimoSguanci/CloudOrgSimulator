import HelperUtils.CreateLogger
import Simulations.Basics.BasicFirstExampleSimulation

object SimulationFirstExample:
  val logger = CreateLogger(classOf[SimulationFirstExample])

  @main def runSimulationFirstExample =
    logger.info("Constructing a cloud model...")
    BasicFirstExampleSimulation.Start()
    logger.info("Finished cloud simulation...")

class SimulationFirstExample
