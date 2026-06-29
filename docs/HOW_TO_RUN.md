# How to Run the Banking App Locally — Complete Step by Step Guide

Follow every step in order. Do not skip any step.

---

## Prerequisites — Install These First

| Tool | Version | Download |
|---|---|---|
| Java (Temurin JDK 17) | 17.0.19+ | https://adoptium.net/temurin/releases/?version=17 |
| Apache Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Git | Latest | https://git-scm.com/download/win |
| Node.js | 20+ | https://nodejs.org |

**Verify installations** — open Command Prompt and run:
```cmd
java -version        (should show 17.x)
mvn -version         (should show 3.9.x)
docker --version     (should show 29.x)
git --version        (should show 2.x)
node --version       (should show v20.x or v24.x)
```

---

## IMPORTANT — Set Java 17 in Every Command Prompt Window

You MUST run this at the start of every new Command Prompt window before running Maven:

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
```

You should see: `openjdk version "17.0.19"`

---

## Step 1 — Clone the Repository

Open Command Prompt:

```cmd
cd C:\Users\your-username
mkdir banking-app
cd banking-app
git clone https://github.com/anooshN/banking-backend.git
cd banking-backend
```

---

## Step 2 — Start Docker Desktop

1. Open **Docker Desktop** from Start Menu
2. Wait for the green whale icon in the taskbar (2-3 minutes)
3. Verify: `docker ps` should show an empty table (no error)

---

## Step 3 — Start Infrastructure Containers

Run this from the `banking-backend` folder:

```cmd
docker-compose up -d postgres-accounts postgres-transactions postgres-payments redis kafka zookeeper schema-registry mongodb
```

Then create the auth database container (separate because Windows uses port 5432):

```cmd
docker run -d ^
  --name banking-backend-postgres-auth-1 ^
  --network banking-backend_banking-network ^
  -e POSTGRES_DB=banking_auth ^
  -e POSTGRES_USER=banking ^
  -e POSTGRES_PASSWORD=banking-secret ^
  -p 5436:5432 ^
  -v banking-backend_postgres-auth-data:/var/lib/postgresql/data ^
  postgres:16-alpine
```

**Verify all containers are running:**
```cmd
docker ps
```

You should see these containers running:
- `banking-backend-postgres-auth-1` → port 5436
- `banking-backend-postgres-accounts-1` → port 5433
- `banking-backend-postgres-transactions-1` → port 5434
- `banking-backend-postgres-payments-1` → port 5435
- `banking-backend-redis-1` → port 6379
- `banking-backend-kafka-1` → port 9092
- `banking-backend-zookeeper-1`
- `banking-backend-schema-registry-1` → port 8085
- `banking-backend-mongodb-1` → port 27017

**Fix currency_code column type** (run once):
```cmd
docker exec banking-backend-postgres-accounts-1 psql -U banking -d banking_accounts -c "ALTER TABLE accounts ALTER COLUMN currency_code TYPE VARCHAR(3);"
docker exec banking-backend-postgres-transactions-1 psql -U banking -d banking_transactions -c "ALTER TABLE transactions ALTER COLUMN currency_code TYPE VARCHAR(3);"
docker exec banking-backend-postgres-payments-1 psql -U banking -d banking_payments -c "ALTER TABLE payments ALTER COLUMN currency_code TYPE VARCHAR(3);"
```

---

## Step 4 — Build the Backend

```cmd
cd C:\Users\your-username\banking-app\banking-backend
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
mvn clean install -DskipTests
```

Wait for: `BUILD SUCCESS` (takes 2-5 minutes first time)

---

## Step 5 — Start Services (Open a New Window for Each)

Start services IN THIS ORDER. Wait for each to show "Started" before starting the next.

### Window 1 — Discovery Server (START FIRST, wait 15 seconds)
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\discovery-server
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
mvn spring-boot:run
```
✅ Wait for: `Started DiscoveryServerApplication`
🌐 Verify: http://localhost:8761 (user: eureka, pass: eureka-secret)

### Window 2 — Auth Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\auth-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5436/banking_auth
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set REDIS_HOST=localhost
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started AuthServiceApplication`

**Note:** If you get Flyway checksum mismatch error, run:
```cmd
docker exec banking-backend-postgres-auth-1 psql -U banking -d banking_auth -c "DELETE FROM flyway_schema_history WHERE version='1';"
docker exec banking-backend-postgres-auth-1 psql -U banking -d banking_auth -c "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (1, '1', 'create users table', 'SQL', 'V1__create_users_table.sql', -479063169, 'banking', NOW(), 100, true);"
```
Then restart auth-service.

### Window 3 — Account Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\account-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/banking_accounts
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set REDIS_HOST=localhost
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started AccountServiceApplication`

### Window 4 — Transaction Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\transaction-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/banking_transactions
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set REDIS_HOST=localhost
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started TransactionServiceApplication`

### Window 5 — Payment Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\payment-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5435/banking_payments
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started PaymentServiceApplication`

### Window 6 — Notification Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\notification-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started NotificationServiceApplication`

### Window 7 — Fraud Detection Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\fraud-detection-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/banking_accounts
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set REDIS_HOST=localhost
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started FraudDetectionServiceApplication`

### Window 8 — User Service
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\user-service
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5436/banking_auth
set SPRING_DATASOURCE_USERNAME=banking
set SPRING_DATASOURCE_PASSWORD=banking-secret
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set SCHEMA_REGISTRY_URL=http://localhost:8085
mvn spring-boot:run
```
✅ Wait for: `Started UserServiceApplication`

### Window 9 — API Gateway (START LAST)
```cmd
cd C:\Users\your-username\banking-app\banking-backend\services\api-gateway
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set JWT_SECRET=my-super-secret-jwt-key-that-is-at-least-32-characters-long
set REDIS_HOST=localhost
mvn spring-boot:run
```
✅ Wait for: `Started ApiGatewayApplication`

---

## Step 6 — Verify Everything is Running

Open Eureka dashboard: http://localhost:8761

You should see ALL these services as UP:
- ACCOUNT-SERVICE (port 8083)
- AUTH-SERVICE (port 8081)
- FRAUD-DETECTION-SERVICE (port 8089)
- NOTIFICATION-SERVICE (port 8086)
- PAYMENT-SERVICE (port 8087)
- TRANSACTION-SERVICE (port 8084)
- USER-SERVICE (port 8082)

---

## Step 7 — Test the API

### Register a New User
```cmd
curl -X POST http://localhost:8081/api/v1/auth/register -H "Content-Type: application/json" -d "{\"email\":\"john@banking.com\",\"password\":\"SecurePass@123\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"phoneNumber\":\"+1234567890\"}"
```
Expected: `{"success":true, "data": {"accessToken": "eyJ..."}}`

### Login
```cmd
curl -X POST http://localhost:8081/api/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"john@banking.com\",\"password\":\"SecurePass@123\"}"
```
Copy the `accessToken` from the response.

### Create a Bank Account
Replace `{userId}` with your userId and `{token}` with your accessToken:
```cmd
curl -X POST "http://localhost:8083/api/v1/accounts/user/{userId}?type=CHECKING&currency=USD" -H "Authorization: Bearer {token}"
```
Expected: `{"success":true, "data": {"accountNumber": "ACC...", "balance": 0}}`

---

## Port Reference

| Service | Port | URL |
|---|---|---|
| Eureka Dashboard | 8761 | http://localhost:8761 |
| API Gateway | 8080 | http://localhost:8080 |
| Auth Service | 8081 | http://localhost:8081 |
| User Service | 8082 | http://localhost:8082 |
| Account Service | 8083 | http://localhost:8083 |
| Transaction Service | 8084 | http://localhost:8084 |
| Schema Registry | 8085 | http://localhost:8085 |
| Notification Service | 8086 | http://localhost:8086 |
| Payment Service | 8087 | http://localhost:8087 |
| Fraud Detection | 8089 | http://localhost:8089 |
| PostgreSQL (auth) | 5436 | |
| PostgreSQL (accounts) | 5433 | |
| PostgreSQL (transactions) | 5434 | |
| PostgreSQL (payments) | 5435 | |
| Redis | 6379 | |
| Kafka | 9092 | |
| MongoDB | 27017 | |

---

## Stopping Everything

To stop all services: Press `Ctrl+C` in each Command Prompt window.

To stop Docker containers:
```cmd
docker-compose down
```

To stop only the auth postgres (manually created):
```cmd
docker stop banking-backend-postgres-auth-1
```
