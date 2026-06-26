# Kubernetes & EKS

## What Is Kubernetes?

Kubernetes (K8s) is an open-source system for running, scaling, and managing containerized applications.

**Simple explanation:** Kubernetes is like a really smart facilities manager for a large office building. You say "I need 3 copies of the auth-service running." The manager finds servers with free capacity, starts the containers, monitors them, replaces ones that crash, and scales up when it gets busy.

---

## Core Kubernetes Objects

### Pod

The smallest deployable unit. Contains one or more containers that share a network and storage.

```yaml
# We don't usually write Pod YAML directly — Deployments manage Pods
# But conceptually each pod looks like:
apiVersion: v1
kind: Pod
metadata:
  name: auth-service-abc123
  namespace: banking
  labels:
    app.kubernetes.io/name: auth-service
spec:
  containers:
    - name: auth-service
      image: 123456789.dkr.ecr.us-east-1.amazonaws.com/auth-service:abc1234
      ports:
        - containerPort: 8081
      env:
        - name: DB_HOST
          value: "banking-auth-cluster.us-east-1.rds.amazonaws.com"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:     # read from Kubernetes Secret
              name: banking-secrets
              key: jwt-secret
      resources:
        requests:
          memory: "512Mi"   # Kubernetes schedules on a node with this much free
          cpu: "250m"        # 250 millicores = 0.25 CPU cores
        limits:
          memory: "1Gi"     # Container killed if it uses more (OOMKilled)
          cpu: "500m"        # Throttled if it uses more (not killed)
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        initialDelaySeconds: 60   # wait 60s before first check (startup time)
        periodSeconds: 10         # check every 10s
        failureThreshold: 3       # 3 failures → restart the pod
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8081
        initialDelaySeconds: 30
        periodSeconds: 5
        failureThreshold: 3       # 3 failures → stop sending traffic to this pod
```

**Liveness vs Readiness:**
- **Liveness:** "Is the process alive?" If no: restart the pod. Used to recover from deadlocks.
- **Readiness:** "Is the pod ready to serve traffic?" If no: remove from load balancer but don't restart. Used during startup (loading config, warming cache) or when temporarily overloaded.

### Deployment

Manages the desired state of pods:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: banking
spec:
  replicas: 2                # keep 2 pods running at all times
  selector:
    matchLabels:
      app.kubernetes.io/name: auth-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1            # create 1 extra pod during update
      maxUnavailable: 0      # never take a pod down before a new one is ready
      # With 2 replicas: during update, briefly have 3 pods (2 old + 1 new)
      # Then remove 1 old pod → 2 pods (1 old + 1 new)
      # Then remove last old pod → 2 pods (both new)
  template:
    metadata:
      labels:
        app.kubernetes.io/name: auth-service
    spec:
      containers:
        - name: auth-service
          image: 123456789.dkr.ecr.us-east-1.amazonaws.com/auth-service:abc1234
          # ... same as Pod spec above
```

### Service

Provides a stable network address for a group of pods:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: banking
spec:
  type: ClusterIP  # only accessible within the cluster
  selector:
    app.kubernetes.io/name: auth-service  # routes to pods with this label
  ports:
    - port: 8081         # service port (what other services connect to)
      targetPort: 8081   # pod port (where the container actually listens)
```

**Why Services?** Pods come and go — they get new IP addresses on every restart. Services provide a stable DNS name (`auth-service.banking.svc.cluster.local`) that always routes to healthy pods, regardless of their current IP.

### HPA — Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-service
  namespace: banking
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  minReplicas: 2       # never scale below 2 (for high availability)
  maxReplicas: 10      # never scale above 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70   # scale up when average CPU > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80   # scale up when average memory > 80%
```

**How it works:**
1. HPA checks CPU/memory every 15 seconds
2. Average CPU across all auth-service pods = 85% (above 70% threshold)
3. HPA calculates: need 85/70 × 2 = 2.4 → round up = 3 pods
4. HPA updates Deployment replicas to 3
5. Deployment creates a new pod
6. New pod starts, traffic distributed across 3 pods
7. CPU drops below 70%
8. HPA scales back down (slowly — 5 minute stabilization window)

### PDB — Pod Disruption Budget

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: auth-service
spec:
  minAvailable: 1    # always keep at least 1 pod running
  selector:
    matchLabels:
      app.kubernetes.io/name: auth-service
```

**Why PDB?** When Kubernetes drains a node (maintenance, node replacement), it starts terminating pods. Without PDB, it might terminate ALL pods at once → service outage. With PDB: "you can only terminate pods if at least 1 remains." Kubernetes respects this and waits for replacement pods to be ready before terminating more.

### Network Policy

Restricts which pods can communicate:

```yaml
# Only auth-service can call account-service
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: account-service-ingress
  namespace: banking
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: account-service
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: transaction-service
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: api-gateway
      ports:
        - port: 8083
```

**Effect:** account-service only accepts connections from transaction-service and api-gateway. If fraud-detection-service (or a compromised service) tries to call account-service directly, the network policy blocks it at the network level.

---

## EKS — Kubernetes on AWS

Amazon EKS manages the Kubernetes control plane (API server, etcd, scheduler). You manage the worker nodes (EC2 instances where pods run).

**Our node groups:**
```hcl
eks_managed_node_groups = {
  system = {
    instance_types = ["t3.medium"]   # for Kubernetes system components
    min_size = 2
    max_size = 4
    desired_size = 2
  }
  banking_services = {
    instance_types = ["m5.xlarge"]   # 4 vCPU, 16GB RAM — for our services
    min_size = 3
    max_size = 20                    # can scale to 20 nodes
    desired_size = 6                 # start with 6
  }
}
```

**Kubernetes add-ons automatically installed:**
- `coredns`: DNS resolution for service names (auth-service.banking → cluster IP)
- `kube-proxy`: Network routing between pods
- `vpc-cni`: AWS-native pod networking (pods get VPC IP addresses)
- `aws-ebs-csi-driver`: Provision EBS volumes as Kubernetes PersistentVolumes
- `aws-load-balancer-controller`: Create AWS ALBs from Kubernetes Ingress objects
