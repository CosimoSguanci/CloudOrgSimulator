import HelperUtils.CreateLogger
import Simulations.SaaS.SaasWorkspaceSimulationVmSchedulerTimeShared

object RunSaasSimulationWorkspaceVmSchedulerTimeShared:
  val logger = CreateLogger(classOf[RunSaasSimulationWorkspaceVmSchedulerTimeShared])

  @main def runSimulation5 =
    logger.info("Constructing a cloud model...")
    SaasWorkspaceSimulationVmSchedulerTimeShared.Start()
    logger.info("Finished cloud simulation...")

class RunSaasSimulationWorkspaceVmSchedulerTimeShared
