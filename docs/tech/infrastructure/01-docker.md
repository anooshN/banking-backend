# Docker & Containers

## What Is Docker?

Docker packages an application and everything it needs to run (JDK, dependencies, OS libraries) into a single portable unit called a **container**.

**Simple explanation:** Imagine a shipping container. It contains everything needed to deliver the goods. It works on any ship, any port, any country. A Docker container contains everything needed to run a service. It works on any developer's laptop, any test server, any cloud.

**The problem Docker solves:** "It works on my machine." With Docker, if it works on your machine, it works everywhere — the container is identical.

---

## Our Dockerfile — Multi-Stage Build

Every service has a Dockerfile. Here's auth-service's:

```dockerfile
# Stage 1: Build
# Use a full JDK image for compilation
FROM eclipse-temurin:17-jre-alpine

# Set working directory inside the container
WORKDIR /app

# Copy the compiled JAR file
# (Maven produced this in target/ directory)
COPY target/*.jar app.jar

# Tell Docker this container listens on port 8081
EXPOSE 8081

# Command to run when container starts
ENTRYPOINT ["java",
    # Use as much RAM as 75% of the container's memory limit
    "-XX:+UseContainerSupport",
    "-XX:MaxRAMPercentage=75.0",
    # G1GC: best garbage collector for server applications
    "-XX:+UseG1GC",
    # Target max 200ms GC pause (reduces latency spikes)
    "-XX:MaxGCPauseMillis=200",
    "-jar", "app.jar"]
```

**For the frontend — multi-stage build:**
```dockerfile
# Stage 1: Build the React app
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci               # ci = clean install (reproducible, uses package-lock.json)
COPY . .
RUN npm run build        # creates /app/dist/ with static files

# Stage 2: Serve with Nginx
# Only copies the built files — node_modules (500MB+) is left behind
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Why multi-stage?**
- Stage 1 image: ~1GB (Node.js + all dev dependencies + source code)
- Stage 2 (final) image: ~25MB (nginx + compiled static files only)
- Smaller images = faster deployment, less attack surface, less storage cost

---

## Docker Compose — Local Development

docker-compose.yml defines all services and their dependencies for local development:

```yaml
services:
  # ── Databases ───────────────────────────────────────────────────────────────
  postgres-auth:
    image: postgres:16-alpine      # specific version (not 'latest') for reproducibility
    environment:
      POSTGRES_DB: banking_auth
      POSTGRES_USER: banking
      POSTGRES_PASSWORD: banking-secret
    ports:
      - "5432:5432"               # host:container — access from localhost:5432
    volumes:
      - postgres-auth-data:/var/lib/postgresql/data  # data persists across restarts
    networks:
      - banking-network
    healthcheck:                   # Kubernetes/Compose won't route to this until healthy
      test: ["CMD-SHELL", "pg_isready -U banking"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 512mb --maxmemory-policy allkeys-lru
    # allkeys-lru: when memory is full, evict least recently used keys
    ports: ["6379:6379"]
    networks: [banking-network]

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]        # starts after zookeeper is running
    ports: ["9092:9092"]
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092
      # localhost:9092: accessible from your machine (outside Docker)
      # kafka:29092: accessible between containers (inside Docker network)
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1  # 1 = local dev (no replication)

  # ── Spring Boot Services ─────────────────────────────────────────────────────
  auth-service:
    build: services/auth-service   # build from the Dockerfile in this directory
    ports: ["8081:8081"]
    depends_on:
      postgres-auth:
        condition: service_healthy  # wait for postgres health check to pass
      redis:
        condition: service_started
      kafka:
        condition: service_started
    environment:
      DB_HOST: postgres-auth        # container name = hostname inside Docker network
      DB_NAME: banking_auth
      DB_USERNAME: banking
      DB_PASSWORD: banking-secret
      REDIS_HOST: redis
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092  # internal Kafka address (between containers)
      JWT_SECRET: dev-secret-at-least-32-characters-here
    networks: [banking-network]

volumes:
  postgres-auth-data:    # named volume: persists data across "docker-compose down/up"
  postgres-accounts-data:
  redis-data:

networks:
  banking-network:
    driver: bridge       # all containers can communicate with each other by container name
```

---

## Key Docker Concepts

### Volumes
```yaml
volumes:
  - postgres-auth-data:/var/lib/postgresql/data
```
Without volumes: data is lost when container stops. With volumes: data persists.

The named volume `postgres-auth-data` is managed by Docker. On Linux: `/var/lib/docker/volumes/postgres-auth-data/`.

### Networks
```yaml
networks:
  - banking-network
```
All containers on the same network can reach each other by container name. `auth-service` connects to `postgres-auth` (the container name) instead of an IP address.

### Healthchecks
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U banking"]
  interval: 10s
  retries: 5
```
Docker/Compose won't route `depends_on: service_healthy` until this check passes. Prevents services from starting before their databases are ready.

---

## JVM in Container — Important Flags

Without container-aware flags, Java sees the host machine's RAM, not the container limit:

```
Host machine RAM: 64GB
Container memory limit: 1GB

Without -XX:+UseContainerSupport:
  Java thinks it has 64GB → allocates large heap → container killed (OOMKilled)

With -XX:+UseContainerSupport (Java 8u191+, Java 11+):
  Java correctly sees 1GB → allocates heap based on container limit

-XX:MaxRAMPercentage=75.0:
  Java uses 75% of container RAM for heap
  For 1GB container: 768MB heap
  Remaining 25%: OS, JVM overhead, off-heap memory (Kafka, Netty, etc.)
```

Always use these flags in containers. Without them, you'll see random OOMKilled pod restarts in Kubernetes.
