import HelperUtils.CreateLogger
import Simulations.SaaS.SaasWorkspaceSimulationBasic

object RunSaasWorkspaceSimulationBasic:
  val logger = CreateLogger(classOf[RunSaasWorkspaceSimulationBasic])

  @main def runSimulation =
    logger.info("Constructing a cloud model...")
    SaasWorkspaceSimulationBasic.Start()
    logger.info("Finished cloud simulation...")

class RunSaasWorkspaceSimulationBasic
