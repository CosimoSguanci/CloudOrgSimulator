import HelperUtils.CreateLogger
import Simulations.SaaS.SaasWorkspaceSimulationCloudletSchedulerSpaceShared

object RunSaasWorkspaceSimulationCloudletSchedulerSpaceShared:
  val logger = CreateLogger(classOf[RunSaasWorkspaceSimulationCloudletSchedulerSpaceShared])

  @main def runSimulation2 =
    logger.info("Constructing a cloud model...")
    SaasWorkspaceSimulationCloudletSchedulerSpaceShared.Start()
    logger.info("Finished cloud simulation...")

class RunSaasWorkspaceSimulationCloudletSchedulerSpaceShared
