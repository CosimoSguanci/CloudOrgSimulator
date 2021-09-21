# Cosimo Sguanci - csguan2@uic.edu - 1st Homework (CS441)


# Overview
## Implemented cloud models
- A SaaS infrastructure inspired by Google Workspace. It was modeled from the point of view of the service provider that is offering some type of cloud services (in particular email, cloud docs and cloud storage) to an organization (such as another company that uses it as a business tool)
- An IaaS/PaaS/FaaS infrastructure that resembles a subset of Amazon Web Services 

Once these two models have been built, they have been simulated under several conditions: different workloads, different policies for allocation of resource and scaling strategies.

In all the simulations, a configuration-oriented approach has been employed, to achieve high parameterizable and customizable simulations, therefore minimizing changes to the source code. Some example of parameters included in the configurations are:
- Datacenter parameters, costs for unit of resources, SAN storage details
- Details about computing resources of Hosts contained in datacenters and VMs to be allocated
- Parameters regarding computing tasks (Cloudlets), including how many cloudlets of different type to be included in the simulation

Moreover, in the case of IaaS, that let customers have higher control about the resources to be reserved for applications, the autoscaling feature is highly customizable by tweaking parameters in the configuration:
```
autoscaling {
    scalingStrategy = 0 #0: horizontal scaling, 1: vertical scaling, 2: both, 3: no elasticity

    horizontalScaling {
        cpuOverloadedThreshold = 0.8
    }

    cpuVerticalScaling {
        enabled = 0
        scalingFactor = 1
        lowerUtilizationThreshold = 0.4
        upperUtilizationThreshold = 0.8
    }

    ramVerticalScaling {
        enabled = 0
        scalingFactor = 1
        lowerUtilizationThreshold = 0.4
        upperUtilizationThreshold = 0.8
    }

    bwVerticalScaling {
        enabled = 0
        scalingFactor = 0.5
        lowerUtilizationThreshold = 0.4
        upperUtilizationThreshold = 0.8
    }
}
```

## SaaS
As explained before, the implemented SaaS model is inspired by Google Workspace. That is, it consists of a set of business services deployed on the cloud and offered to customers. In a SaaS model customers do not have the possibility to change parameters, policies or other characteristics of the infrastructure. As an example, customers cannot handle the autoscaling of the system. These abstractions lead to a simplified management for clients: the main drawback is its low flexibility that can cause, for example, degradation of performance.

In the model, the `CloudletSimple` class has been extended to be able to specify the type of task submitted by the customer to the infrastructure. To have a representative subset of the typical services that are offered, the `WorkspaceCloudlet` can be of one of the following types:
- `EMAIL` (e.g., the Gmail service)
- `CLOUD_DOCS` (e.g., Google Docs)
- `CLOUD_STORAGE` (e.g., Google Drive)

The last type of task differs from the other regarding the main resource needed, that is storage capability. These characteristics has been modeled by making each `Cloudlet` of this kind require a file (3 types of files can be requested, based on the size of the file itself). Files are maintained and provided to customers by making use of Storage Area Networks (SANs) in each datacenter.

#### Main parameters
- Number of datacenters = 2 
- Number of SANs for each datacenter = 2 (each file is replicated in each datacenter)
- SAN Bandwidth = 10 Gbps
- costPerSecond (CPU) = 0.01
- costPerMem = 0.02
- costPerStorage = 0.001
- costPerBw = 0.005
- Number of hosts per datacenter = 25
- Host (AMD Epyc 7313P taken as reference):
  - 16 core
  - 150000 MIPS per core
- Number of VMs to be allocated = 120
- Number of tasks (cloudlets):
  - numEmailCloudlets = 100
  - numDocsCloudlets = 100
  - numStorageCloudlets = 100
- No autoscaling

### SaasWorkspaceSimulationBasic

#### Policies
This simulation makes use of the most basic policies, such as:
- `VmAllocationPolicySimple` to allocate VMs to Hosts
- `VmSchedulerSpaceShared` to allocate host PEs to VMs
- `CloudletSchedulerTimeShared` to schedule Cloudlets to be executed on VMs

#### Results
Results have been measured both in terms of Cloudlets execution time and costs.

```
Time to execute all cloudlets: 122978347 s
Total cost: 2.95E8$
```

The first result that has been found is that, with the basic configuration, the execution time of tasks tends to explode, and consequently are also processing cost. This is mainly due to the `CloudletSchedulerTimeShared`, as will be clear in the next simulation. Obviously, these results are not feasible in a real world scenario, so it is necessary to change some policies in the model.

### SaasWorkspaceSimulationImproved


#### Results
Simply by changing the `CloudletScheduler` from `CloudletSchedulerTimeShared` to `CloudletSchedulerSpaceShared` it is possible to notice an enormous improvement:

```
Time to execute all cloudlets: 40 s
Total cost: 2046.62$
```

This is due to the fact that in the time-shared scheduler we are trying to be "fair" in the provision of resources by dedicate a small slice time to all the cloudlets that are requesting a certain VM and continuously alternating them, therefore not creating waiting lists. In the previous simulation, this policy was leading to fast degradation of resource whenever #VM < #cloudlets, that is a realistic situation since in SaaS we (as cloud providers) are in charge of handling the scaling of resources, and it is very likely to have unexpected "spikes" in workload. On the other hand, a space-shared scheduler enables the use of a waiting list of cloudlets to access resource, also reducing the overhead that is generated to continuously switch the cloudlet that is assigned to a VM.


