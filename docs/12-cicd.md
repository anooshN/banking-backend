# 12. CI/CD — How Code Gets to Production

## The Simple Version

CI/CD stands for Continuous Integration / Continuous Deployment.

**Simple analogy:** Imagine a car factory. Every time a worker adds a new part, the factory immediately tests that the car still runs (CI), and if tests pass, automatically moves the car down the production line (CD).

Without CI/CD: you build the entire car and only test it at the end. Problems are caught late and expensive to fix.

With CI/CD: every small change is tested immediately. Problems are caught within minutes.

---

## The Git Workflow (Gitflow)

```
main        -> production (protected, requires 1 approval)
  |
develop     -> integration (feature branches merge here)
  |
feature/*   -> where you write code
```

Rules:
- Nobody pushes directly to main. Everything is reviewed.
- develop always has working, tested code.
- Features are isolated — if your branch breaks, it doesn't affect other branches.

---

## CI Pipeline (GitHub Actions)

Every push to develop or any feature branch triggers automatically:

1. **Checkout code** — download from GitHub
2. **Set up Java 17** — install runtime
3. **Build with Maven** — compile all 14 services + 5 shared libraries. Syntax error? FAIL.
4. **Run Tests** — all unit + integration tests. Test fails? FAIL. Developer notified.
5. **SonarQube** — code quality: coverage > 70%? No security vulnerabilities? FAIL if not.
6. **Build Docker Images** — create container image for each service, tagged with Git commit SHA
7. **Push to ECR** — upload images to Amazon's container registry

**Matrix Build:** Steps 6-7 run in parallel for all 14 services simultaneously. Build time: ~5 minutes instead of ~30.

---

## CD Pipeline — Deploy to Production

When a Pull Request merges into main:

1. **Configure AWS credentials**
2. **Connect to EKS cluster**
3. **ArgoCD Sync** — ArgoCD compares what's in Git (desired state) vs what's running in Kubernetes (actual state). If different → update Kubernetes. Rolling update, no downtime.
4. **Wait for health** — poll until all pods are Running, passing health checks, ready for traffic. If something fails → ArgoCD automatically rolls back.

---

## ArgoCD — GitOps

ArgoCD watches your Git repository. When the repository changes (new image tag, config change), ArgoCD automatically updates Kubernetes to match.

**GitOps principle:** Your Git repository IS the source of truth. Instead of running kubectl commands manually, you commit changes to Git. ArgoCD applies them automatically. All changes are tracked, auditable, and reversible.

If someone accidentally deletes a deployment directly on the cluster, ArgoCD detects the drift within seconds and recreates it. Git always wins.

---

## Frontend CI/CD

1. TypeScript type check (catch type errors before runtime)
2. ESLint (code style and potential bugs)
3. Jest unit tests (70%+ coverage required)
4. Build (npm run build — create optimized static files)
5. Push Docker image to ECR
6. On merge to main: invalidate CloudFront cache (users get the new version within seconds)

---

## Secrets — Never in Code

Source code never contains passwords or API keys. They live in:
- **GitHub Secrets:** For CI/CD pipelines (AWS_ACCESS_KEY_ID, ECR_REGISTRY, SONAR_TOKEN)
- **Kubernetes Secrets:** For runtime (JWT_SECRET, DB_PASSWORD)
- **Future:** AWS Secrets Manager for automatic password rotation

The CI pipeline can only build and push images. It cannot access production databases. Separate credentials for separate jobs.
