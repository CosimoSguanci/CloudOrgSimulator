import HelperUtils.CreateLogger
import Simulations.SaaS.SaasWorkspaceSimulationBasic

object RunSaasSimulationWorkspaceBasic:
  val logger = CreateLogger(classOf[RunSaasSimulationWorkspaceBasic])

  @main def runSimulationSuiteServices =
    logger.info("Constructing a cloud model...")
    SaasWorkspaceSimulationBasic.Start()
    logger.info("Finished cloud simulation...")

class RunSaasSimulationWorkspaceBasic
