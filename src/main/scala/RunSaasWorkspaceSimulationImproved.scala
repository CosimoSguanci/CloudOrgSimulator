import HelperUtils.CreateLogger
import Simulations.SaaS.SaasWorkspaceSimulationImproved

object RunSaasWorkspaceSimulationImproved:
  val logger = CreateLogger(classOf[RunSaasWorkspaceSimulationImproved])

  @main def runSimulation2 =
    logger.info("Constructing a cloud model...")
    SaasWorkspaceSimulationImproved.Start()
    logger.info("Finished cloud simulation...")

class RunSaasWorkspaceSimulationImproved
