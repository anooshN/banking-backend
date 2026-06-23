# 11. Infrastructure — Cloud and Kubernetes

## The Simple Version

The app needs to run somewhere. That somewhere is Amazon Web Services (AWS). And on AWS, all the pieces run inside Kubernetes — a system that manages containers (small, portable boxes that contain each service).

**Analogy:** Kubernetes is like a shipping port. Each service is a shipping container. Kubernetes decides which ship (server) each container goes on, makes sure containers are running, replaces broken ones, and scales up when demand is high.

---

## AWS Services Used

| AWS Service | What It Is | Used For |
|---|---|---|
| EKS | Managed Kubernetes | Running all 14 microservices as containers |
| RDS Aurora | Managed PostgreSQL | auth, accounts, transactions, payments databases |
| ElastiCache | Managed Redis | Token storage, rate limiting, caching |
| MSK | Managed Kafka | Event streaming between services |
| S3 | Object storage | Statement files, backups |
| CloudFront | CDN | Serving the React frontend globally fast |
| ECR | Container registry | Storing Docker images |
| IAM | Identity management | Permissions and security |
| VPC | Virtual network | Isolated network for all resources |

---

## VPC — The Private Network

Everything runs inside a private network the internet cannot directly access. The only way in is through specific, controlled entry points.

```
Internet
    |
    v
Public Subnets  (load balancers, NAT Gateway)
    |
Private Subnets (EKS nodes, RDS, ElastiCache, MSK)
                 No direct internet access
```

The databases, Kafka, and Redis are in private subnets — no internet exposure. They can only be accessed by services running inside the same VPC.

---

## EKS — Kubernetes on AWS

**What is a container?**
A container packages the application code AND everything it needs (Java runtime, dependencies) into a self-contained unit. It runs the same way everywhere — on your laptop, on a test server, in production.

**What is Kubernetes?**
A system that manages containers: runs them on servers, restarts them if they crash, scales them up/down based on traffic, balances traffic between multiple instances, rolls out updates without downtime.

**Key Kubernetes Concepts:**

- **Pod:** The smallest unit. Usually one container per pod.
- **Deployment:** "I want 3 pods of auth-service running at all times." If one crashes, the deployment creates a new one automatically.
- **Service:** A stable network address for a group of pods. Even as pods come and go, the Service address stays the same.
- **HPA:** "If CPU usage goes above 70%, add more pods. If it drops below 50%, remove some."
- **PDB:** "During an update or maintenance, always keep at least 1 pod running." Prevents downtime.

Our HPA config for each service: minReplicas: 2, maxReplicas: 10, targetCPU: 70%.

---

## Terraform — Infrastructure as Code

Instead of clicking buttons in the AWS console, you write code that describes what you want. Terraform reads the code and creates everything.

Why better than clicking?
- **Reproducible:** Run the same code, get the exact same infrastructure every time
- **Version controlled:** Infrastructure changes go through Git, reviewed like code
- **Documented:** The code IS the documentation
- **Disposable:** Can destroy and recreate the entire environment with one command

Our Terraform creates: VPC with 3 availability zones, EKS cluster with 2 node groups, 2 RDS Aurora clusters, ElastiCache Redis (3 nodes), MSK Kafka (3 broker nodes), all security groups.

---

## Helm — Kubernetes Package Manager

Helm is like npm install but for Kubernetes. Instead of writing 5 YAML files manually for each service, you write one values.yaml and Helm generates all the Kubernetes manifests.

Without Helm, deploying auth-service requires writing manually: Deployment.yaml, Service.yaml, HPA.yaml, PDB.yaml, ServiceAccount.yaml.

With Helm:
```bash
helm install auth-service ./charts/auth-service --set image.tag=abc123
```

Our Helm charts handle: container image/tag, environment variables (from Kubernetes Secrets), resource limits (memory: 1Gi, CPU: 500m), liveness/readiness probes, HPA and PDB, service account.

---

## Availability Zones

We use 3 AZs in us-east-1. Our resources are spread across all 3. If one entire data center fails, the other 2 keep serving traffic. The user notices nothing.

RDS Aurora replicates across 3 AZs. ElastiCache runs 3 nodes across 3 AZs. MSK runs 3 broker nodes across 3 AZs. EKS runs worker nodes across 3 AZs.

---

## Zero Downtime Deployments (Rolling Update)

When you deploy a new version:
```
Before:  [v1.0] [v1.0] [v1.0]
Step 1:  [v1.1] [v1.0] [v1.0]  <- start new pod, wait for health
Step 2:  [v1.1] [v1.1] [v1.0]  <- start another, remove old
Step 3:  [v1.1] [v1.1] [v1.1]  <- all updated
```
Traffic always served by at least 2 pods throughout. Users experience no downtime.
