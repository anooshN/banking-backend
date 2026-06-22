# Banking Backend — Microservices Architecture

Production-grade Banking Application built with Java 17, Spring Boot 3.x, Spring Cloud.

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.x, Spring WebFlux, Spring Batch
- **Microservices**: Spring Cloud Gateway, Eureka, Config Server, Feign, Resilience4j
- **Security**: OAuth2, JWT (RS256), SAML, OIDC, mTLS, RBAC
- **Messaging**: Apache Kafka (Avro, Schema Registry, Exactly-Once), RabbitMQ, AWS MSK, SQS/SNS
- **Databases**: PostgreSQL, MongoDB, Redis, Cassandra, DynamoDB, Elasticsearch
- **DevOps**: Docker, Kubernetes (EKS), Helm, ArgoCD, Terraform, GitHub Actions
- **Monitoring**: Prometheus, Grafana, ELK Stack, AppDynamics, Zipkin

## Microservices
| Service | Port | Database |
|---|---|---|
| api-gateway | 8080 | Redis |
| discovery-server | 8761 | In-memory |
| config-server | 8888 | Git |
| auth-service | 8081 | PostgreSQL + Redis |
| user-service | 8082 | PostgreSQL |
| account-service | 8083 | PostgreSQL + Redis |
| transaction-service | 8084 | PostgreSQL + Kafka |
| payment-service | 8085 | PostgreSQL |
| notification-service | 8086 | MongoDB |
| audit-service | 8087 | Cassandra |
| report-service | 8088 | PostgreSQL + S3 |
| fraud-detection-service | 8089 | Redis + DynamoDB |
| card-service | 8090 | PostgreSQL |
| loan-service | 8091 | PostgreSQL |

## Branching Strategy (Gitflow)
- `main` — production only, protected
- `develop` — integration branch
- `feature/*` — new features
- `release/*` — release candidates
- `hotfix/*` — production fixes

## Getting Started
```bash
git clone https://github.com/anooshN/banking-backend.git
cd banking-backend
docker-compose up -d
mvn clean install
```
