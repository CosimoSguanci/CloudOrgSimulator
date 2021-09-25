# Overview
## Implemented cloud models
- A SaaS infrastructure inspired by Google Workspace. It was modeled from the point of view of the service provider that is offering some type of cloud services (in particular email, cloud docs and cloud storage) to an organization (such as another company that uses them as a business tools)
- An IaaS/PaaS/FaaS infrastructure that resembles a subset of Amazon Web Services

Once these models have been built, they have been simulated under several conditions: different workloads, different policies for allocation of resource and scaling strategies.

In all the simulations, a configuration-oriented approach has been employed, to achieve high parameterizable and customizable simulations. Some example of parameters included in the configurations are:
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
As explained before, the implemented SaaS model is inspired by Google Workspace. That is, it consists of a set of business services deployed on the cloud and offered to organizations. In a SaaS model customers do not have the possibility to change parameters, policies or other characteristics of the infrastructure. As an example, customers cannot handle the autoscaling of the system. These abstractions lead to a simplified management for clients: the main drawback is its low flexibility that can cause, for example, degradation of performance.

In the model, the `CloudletSimple` class has been extended to be able to specify the type of task submitted by the customer to the infrastructure. To have a representative subset of the typical services that are offered, the `WorkspaceCloudlet` can be of one of the following types:
- `EMAIL` (e.g., Gmail)
- `CLOUD_DOCS` (e.g., Google Docs)
- `CLOUD_STORAGE` (e.g., Google Drive)

The last type of task differs from the other regarding the main resource needed, that is storage capability. These characteristics have been modeled by making each `Cloudlet` of this kind require a file (3 types of files can be requested, based on the size of the file itself). Files are maintained and provided to customers by making use of Storage Area Networks (SANs) in each datacenter.

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

### SaasWorkspaceSimulationCloudletSchedulerSpaceShared

#### Results
By changing the `CloudletScheduler` from `CloudletSchedulerTimeShared` to `CloudletSchedulerSpaceShared` it is possible to notice a substantial improvement:

```
Time to execute all cloudlets: 40 s
Total cost: 5733$
```

This is due to the fact that with the time-shared scheduler we are trying to be "fair" in the provision of resources by dedicainge a small slice of time to all the cloudlets that are requesting a certain VM and continuously alternating them, therefore not creating waiting lists. In the previous simulation, this policy was leading to fast degradation of resource whenever #VM < #cloudlets, that is a realistic situation since in SaaS we (as cloud providers) are in charge of handling the scaling of resources, and it is very likely to have unexpected "spikes" in workload. On the other hand, a space-shared scheduler enables the use of a waiting list of cloudlets to access resource, also reducing the overhead that is generated to continuously switch the cloudlet that is assigned to a VM.

### SaasWorkspaceSimulationVmSchedulerTimeShared

It's interesting to explore the possibilities that are offered by other policies regarding VM scheduling. For instance, in this simulation we are changing it from `VmSchedulerSpaceShared` to `VmSchedulerTimeShared` (in conjunction with `CloudletSchedulerTimeShared`). Doing this makes it feasible to handle situations in which we have an insufficient total number of Host PEs for all the VMs. As a matter of fact, this VM scheduling policy allows the sharing of the same PEs by multiple VMs.

The configuration is changed as follows:
- Num of datacenters = 2
- Num of hosts per datacenter = 5
- Host PEs = 2
- MIPS for PE = 1000
- Num of VMs = 40
- MIPS for VM = 500
- PEs for VM = 1
- 40 cloudlets, 1 PE for every cloudlet

We can notice that, in this case, we have 20 PEs available in hosts but the cumulative VMs requirements are 40 PEs. However, the total MIPS is sufficient to satisfy the demand, therefore the new VM scheduling policy lead to the following results:

```
Time to execute all cloudlets: 40 s
Total cost: 1886.47$
```

On the other hand, using `VmSchedulerSpaceShared` in this situation causes the simulation to fail even to finish in a reasonable amount of time.

### IaaS / PaaS / FaaS
The second category of simulation that have been performed regard a cloud model that resembles a subset of the services offered by AWS. In particular, with respect to the previous simulation, this model is implementing a scenario of a larger scale, offering more flexible services.

### IaasPaasFaasSimulationBasic

#### Main parameters
- Number of datacenters = 10
- Number of SANs for each datacenter = 5 (each file is replicated in each datacenter)
- SAN Bandwidth = 10 Gbps
- costPerSecond (CPU) = 0.01
- costPerMem = 0.02
- costPerStorage = 0.001
- costPerBw = 0.005
- Number of hosts per datacenter = 100
- Host (AMD Epyc 7313P taken as reference):
    - 16 core
    - 150000 MIPS per core
- Number of VMs to be allocated = 3000
- Number of tasks (cloudlets):
    - num of IaaS tasks = 2000
    - num of PaaS tasks = 2000
    - num of FaaS tasks = 2500
- Autoscaling enabled

#### Policies

Due to the high level of configurability implemented in these simulations, autoscaling can be completely customized through configuration files. It is possible to enable horizontal scaling, vertical scaling, and also both in combination.

In this model there is a clear distinction between IaaS/PaaS and FaaS tasks. Indeed, the former is a relatively new deployment model that has different requirements. In particular, FaaS cloudlets are much less expensive in terms of computing power, and they are allocated to specific lightweight VMs. To achieve this, a dedicated broker has been created to assign this kind of cloudlets to their specific VMs.
To test elasticity capabilities that can be enforced by customers, the list of cloudlets is not static but changes during the simulation. Periodically, new cloudlets are added to the queue (the number of newly created tasks and the duration until they are created can be changed in configurations). For the first simulation:

- 12 cloudlets are created (4 IaaS, 4 PaaS, 4 FaaS) every time the simulation clock advances
- New cloudlets are added until the simulation clock arrives at 2.0 (needed to avoid an infinite simulation)

The other policies employed are the following:

- `VmAllocationPolicySimple` to allocate VMs to Hosts
- `VmSchedulerSpaceShared` to allocate host PEs to VMs
- `CloudletSchedulerSpaceShared` to schedule Cloudlets to be executed on VMs

#### Results

Without any scaling policy, the results are the following:

```
Total cost: 148291.86$
Max execution time for cloudlet: 86.58 s
Total number of finished cloudlets: 27296
```

What was observed is that enabling vertical scaling leads to lower costs in this case. This could be due to the fact that resources are over-provisioned in this case, as is possible to verify by checking that multiple times during the simulation the `isVmUnderloaded` function returns `true` and the `VerticalVmScaling` requests a downscaling. With vertical scaling enabled, resources can be scaled down in order to reduce costs, only slightly worsening the maximum latency time. In fact, enabling vertical elasticity for CPUs, RAMs and BW, yields to the following results:

```
Total cost: 11700.47$
Max execution time for cloudlet: 87.36 s
Total number of finished cloudlets: 32780
```

Changing the VM allocation policy to `VmAllocationPolicyBestFit` slightly reduces the costs but also the total number of finished cloudlets is lower. For these reasons, and also considering its computational complexity (O(N) where N is the number of hosts), it may not be suitable for a large scale scenario.

To explore more realistic scenario, it's possible to randomize the utilization of resources, by using  `UtilizationModelStochastic`. In our simulations it was enough to create the IaaS and PaaS cloudlets passing `UtilizationModelType.STOCHASTIC` to `createCloudletsIaaSPaaS`. This utilization model has not been used for FaaS cloudlets because it's reasonable to assume that the FaaS Tasks always fully use their lightweight VM to terminate the task as soon as possible.
The difference in terms of cost with previous simulations is interesting (results are shown as average over a set of simulation instances, since we are modeling a stochastic behaviour):

```
Total cost: 13107.82$
Max execution time for cloudlet: 103.45 s
Total number of finished cloudlets: 32780
```

The results show higher cost with respect to the simulation with `UtilizationModelStochastic`. This is probably due to the fact that performing an efficient vertical scaling is more difficult in this setting, therefore if before the resources were downscaled at the start of the simulation and then substantially left unchanged, now the resources must be continuously added and removed from the system. This leads to over-provisioning of resources and overhead for allocation/deallocating them, that is reflected by the higher cloudlet maximum execution time.

## IaasPaasFaasCloudletRandomArrivalSimulation
In this case the main result that we're investigating is how many cloudlets can be completed in a fixed amount of time (on average) when the arrival of new cloudlets is modelled as a random process. 
That is, every time the simulation clock advances, with probability configurable in the .conf file, we're creating new cloudlets and submitting them to the broker. The objective here is to evaluate how different autoscaling techniques behave in this scenario, starting from a situation in which few resources are provisioned.

### Main parameters
- Duration of the simulation = 100 s
- Probability of new cloudlets to be added = 0.5
- Number of new cloudlets created = 12 (3 IaaS, 3 PaaS, 3 FaaS)

### Results
5 simulations for each scaling strategy have been performed, we report here the average results.

#### No autoscaling
```
Average cost: 29278$
Average max execution time for cloudlet: 89.5 s
Average number of finished cloudlets: 10660
```

#### Horizontal autoscaling
```
Average cost: 390998$
Average max execution time for cloudlet: 87.34 s
Average number of finished cloudlets: 12102
```

#### Vertical autoscaling
```
Average cost: 15072$
Average max execution time for cloudlet: 89.5 s
Average number of finished cloudlets: 10472
```

### Vertical/Horizontal autoscaling
```
Average cost: 373715$
Average max execution time for cloudlet: 87.53 s
Average number of finished cloudlets: 13904
```

The last adopted policy is the most performant. As a matter of fact, in this case the horizontal scaling strategy is very well-suited for increasing the number of VMs and therefore the requests that can be handled in a certain amount of time (it is better in terms of throughput), while the vertical scaling is able to reduce costs by scaling down resources when they are not needed.
However, the cost of the last policy could be prohibitive for some kind of applications, that could tolerate the loss of performance (~25%) of the policy that only involves vertical scaling.
Further improvements of this simulation could be:
- Make the number of new cloudlets that are periodically created random (not only the rate of new arrivals), and increasing the number of simulations to compute the average (to improve precision of the measurement)
- Also consider a configuration that could favor vertical scaling policies, such as one in which there are enough VMs, but they have (in the starting situation) poor performance in terms of MIPS, RAM capacity and bandwidth.