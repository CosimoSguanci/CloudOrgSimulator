import HelperUtils.CreateLogger
import Simulations.MapReduce.MapReduceSimulation

object RunMapReduceSimulation:
  val logger = CreateLogger(classOf[RunMapReduceSimulation])

  @main def runSimulationMapReduce =
    logger.info("Constructing a cloud model...")
    MapReduceSimulation.Start()
    logger.info("Finished cloud simulation...")

class RunMapReduceSimulation
