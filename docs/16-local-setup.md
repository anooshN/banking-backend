# 16. How to Run Locally

## Prerequisites

Install these before starting:

| Tool | Version | Install |
|---|---|---|
| Java | 17+ | https://adoptium.net/ |
| Maven | 3.9+ | https://maven.apache.org/ |
| Docker | Latest | https://docker.com/ |
| Docker Compose | Latest | Included with Docker Desktop |
| Node.js | 20+ | https://nodejs.org/ |
| Git | Latest | https://git-scm.com/ |

---

## Step 1 — Clone the Repositories

```bash
git clone https://github.com/anooshN/banking-backend.git
git clone https://github.com/anooshN/banking-frontend.git
```

---

## Step 2 — Start Infrastructure with Docker Compose

The backend repo includes a docker-compose.yml that starts all infrastructure (databases, Kafka, Redis, etc.) plus the Spring Boot services.

```bash
cd banking-backend

# Start ONLY infrastructure (databases, Redis, Kafka, etc.) first
docker-compose up -d postgres-auth postgres-accounts postgres-transactions postgres-payments redis kafka zookeeper schema-registry mongodb
```

Wait about 30 seconds for everything to start. Check they're running:
```bash
docker-compose ps
```

All should show "healthy" or "running".

---

## Step 3 — Build the Backend

```bash
cd banking-backend
mvn clean install -DskipTests
```

This compiles all 14 services and 5 shared libraries. Takes 2-3 minutes the first time.

---

## Step 4 — Start Services in Order

Services have dependencies. Start them in this order:

```bash
# 1. Service discovery (others register here)
cd services/discovery-server && mvn spring-boot:run &

# Wait 15 seconds

# 2. Config server (others fetch config here)
cd services/config-server && mvn spring-boot:run &

# Wait 15 seconds

# 3. Gateway (front door)
cd services/api-gateway && mvn spring-boot:run &

# 4. Business services (any order)
cd services/auth-service && mvn spring-boot:run &
cd services/account-service && mvn spring-boot:run &
cd services/transaction-service && mvn spring-boot:run &
cd services/payment-service && mvn spring-boot:run &
cd services/notification-service && mvn spring-boot:run &
cd services/user-service && mvn spring-boot:run &
cd services/card-service && mvn spring-boot:run &
cd services/loan-service && mvn spring-boot:run &
```

OR start everything with Docker Compose:
```bash
docker-compose up -d
```

---

## Step 5 — Verify the Backend

Check all services are registered in Eureka:
```
http://localhost:8761
Username: eureka
Password: eureka-secret
```

You should see all services listed as UP.

Check the gateway health:
```
http://localhost:8080/actuator/health
```

Should return: `{"status": "UP"}`

Try the API (Swagger UI):
```
http://localhost:8080/swagger-ui.html
```

---

## Step 6 — Start the Frontend

```bash
cd banking-frontend

# Install dependencies (first time only)
npm install

# Start the development server
npm run dev
```

The app opens at: http://localhost:5173

---

## Step 7 — Create a Test User

The database starts empty. Create a user via the API:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@banking.com",
    "password": "SecurePass@123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

You will get back an accessToken and refreshToken. Now you can log in at http://localhost:5173/login.

---

## Environment Variables

The services have sensible defaults for local development in their application.yml files. For production-like testing, create a .env file:

```bash
# banking-backend/.env
JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
DB_HOST=localhost
DB_USERNAME=banking
DB_PASSWORD=banking-secret
REDIS_HOST=localhost
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

---

## Ports Reference

| Service | Port | URL |
|---|---|---|
| API Gateway | 8080 | http://localhost:8080 |
| Eureka Dashboard | 8761 | http://localhost:8761 |
| Config Server | 8888 | http://localhost:8888 |
| auth-service | 8081 | http://localhost:8081 |
| user-service | 8082 | http://localhost:8082 |
| account-service | 8083 | http://localhost:8083 |
| transaction-service | 8084 | http://localhost:8084 |
| payment-service | 8085 | http://localhost:8085 |
| notification-service | 8086 | http://localhost:8086 |
| audit-service | 8087 | http://localhost:8087 |
| report-service | 8088 | http://localhost:8088 |
| fraud-detection-service | 8089 | http://localhost:8089 |
| card-service | 8090 | http://localhost:8090 |
| loan-service | 8091 | http://localhost:8091 |
| PostgreSQL (auth) | 5432 | |
| PostgreSQL (accounts) | 5433 | |
| PostgreSQL (transactions) | 5434 | |
| PostgreSQL (payments) | 5435 | |
| Redis | 6379 | |
| Kafka | 9092 | |
| Schema Registry | 8085 | http://localhost:8085 |
| MongoDB | 27017 | |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3001 | http://localhost:3001 (admin/admin) |
| Kafka UI | 8090 | http://localhost:8090 |
| Frontend | 5173 | http://localhost:5173 |

---

## Running Tests

```bash
# All tests
mvn test

# Single service
cd services/auth-service && mvn test

# With coverage report
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```

Frontend tests:
```bash
cd banking-frontend

# Unit tests
npm test

# E2E tests (requires backend running)
npm run test:e2e
```

---

## Common Issues

**"Connection refused" when service starts:**
The service cannot connect to its dependency. Make sure Docker Compose services are running and healthy first.

**"Could not locate PropertySource":**
Config server is not running or not reachable. Start it before other services.

**Port already in use:**
Another process is on that port. Find and kill it:
```bash
lsof -i :8080
kill -9 <PID>
```

**Eureka shows service DOWN:**
The service is running but failing health checks. Check logs for errors:
```bash
docker-compose logs auth-service
# or
mvn spring-boot:run 2>&1 | tail -50
```
