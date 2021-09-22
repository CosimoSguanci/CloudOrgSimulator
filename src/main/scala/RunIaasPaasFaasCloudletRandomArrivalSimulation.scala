import HelperUtils.CreateLogger
import Simulations.IaaS_PaaS_FaaS.IaasPaasFaasCloudletRandomArrivalSimulation

object RunIaasPaasFaasCloudletRandomArrivalSimulation:
  val logger = CreateLogger(classOf[RunIaasPaasFaasCloudletRandomArrivalSimulation])

  @main def runSimulation4 =
    logger.info("Constructing a cloud model...")
    IaasPaasFaasCloudletRandomArrivalSimulation.Start()
    logger.info("Finished cloud simulation...")

class RunIaasPaasFaasCloudletRandomArrivalSimulation
