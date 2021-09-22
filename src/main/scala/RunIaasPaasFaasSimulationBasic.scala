import HelperUtils.CreateLogger
import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasSimulationBasic

object RunIaasPaasFaasSimulationBasic:
  val logger = CreateLogger(classOf[RunIaasPaasFaasSimulationBasic])

  @main def runSimulation3 =
    logger.info("Constructing a cloud model...")
    IaasPaasFaasSimulationBasic.Start()
    logger.info("Finished cloud simulation...")

class RunIaasPaasFaasSimulationBasic
