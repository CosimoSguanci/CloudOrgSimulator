# Cosimo Sguanci - csguan2@uic.edu - 1st Homework (CS441)


# Overview
## Implemented cloud models
- A SaaS infrastructure inspired by Google Workspace. It was modeled from the point of view of the service provider that is offering some type of cloud services (in particular email, cloud docs and cloud storage) to an organization (such as another company that uses it as a business tool)
- An IaaS/PaaS/FaaS infrastructure that resembles a subset of Amazon Web Services .

Once these two models have been built, they have been simulated under several conditions: different workloads, different policies for allocation of resource and scaling strategies.

## SaaS
As explained before, the implemented SaaS model is inspired by Google Workspace. That is, it consists of a set of business services deployed on the cloud and offered to customers. In a SaaS model customers do not have the possibility to change parameters, policies or other characteristics of the infrastructure. As an example, customers cannot handle the autoscaling of the system. These abstractions lead to a simplified management for clients: the main drawback is its low flexibility that can cause, for example, degradation of performance.

In the model, the `CloudletSimple` class has been extended to be able to specify the type of task submitted by the customer to the infrastructure. To have a representative subset of the typical services that are offered, the `WorkspaceCloudlet` can be of one of the following types:
- `EMAIL` (e.g., the Gmail service)
- `CLOUD_DOCS` (e.g., Google Docs)
- `CLOUD_STORAGE` (e.g., Google Drive)

The last type of task differs from the other regarding the main resource needed, that is storage capability. These characteristics has been modeled by making each `Cloudlet` of this kind require a file (3 types of files can be requested, based on the size of the file itself). Files are maintained and provided to customers by making use of Storage Area Networks (SANs) in each datacenter.

