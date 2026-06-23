# 2. The Big Picture — Architecture

## The Simple Version

Imagine a big restaurant.

- The **customer** walks in and talks to the **waiter** (this is the API Gateway)
- The waiter takes the order to the right **kitchen station** — one station for grilling, one for salads, one for desserts (these are the microservices)
- Each station has its own **ingredients storage** (these are the databases)
- The kitchen uses an **order ticket board** where stations post updates for each other (this is Kafka)
- Everything is filmed by **security cameras** (this is the monitoring and audit system)

---

## The Real Architecture Diagram

```
                        INTERNET
                            │
                    ┌───────▼────────┐
                    │  CloudFront    │  (serves the React website fast, worldwide)
                    │  + React App   │
                    └───────┬────────┘
                            │ HTTPS
                    ┌───────▼────────┐
                    │  API Gateway   │  (the single front door — port 8080)
                    │  (Spring Cloud │   validates JWT, routes requests,
                    │   Gateway)     │   rate limits, circuit breaks
                    └──┬──┬──┬──┬───┘
                       │  │  │  │
          ┌────────────┘  │  │  └──────────────┐
          │               │  │                  │
    ┌─────▼──────┐  ┌─────▼──────┐  ┌──────────▼────┐
    │   auth     │  │  account   │  │  transaction  │  ... and 11 more services
    │  service   │  │  service   │  │    service    │
    └─────┬──────┘  └─────┬──────┘  └──────┬────────┘
          │               │                  │
    ┌─────▼──────┐  ┌─────▼──────┐  ┌──────▼────────┐
    │ PostgreSQL │  │ PostgreSQL │  │  PostgreSQL   │
    │  (auth DB) │  │(accounts)  │  │(transactions) │
    └────────────┘  └────────────┘  └───────────────┘
                            │
                    ┌───────▼────────┐
                    │     KAFKA      │  (message bus — services publish events here)
                    └───────┬────────┘
                            │
           ┌────────────────┼────────────────┐
           │                │                │
    ┌──────▼─────┐  ┌───────▼──────┐  ┌─────▼──────────┐
    │notification│  │    audit     │  │fraud detection │
    │  service   │  │   service    │  │    service     │
    └────────────┘  └──────────────┘  └────────────────┘
```

---

## Three Layers

### Layer 1 — The Frontend (what the user sees)
A React web application. Lives on CloudFront (Amazon's global CDN). When you open the banking app in your browser, you are downloading this.

### Layer 2 — The Backend (where the logic lives)
14 separate Spring Boot services. Each one does one job. They talk to each other through the API Gateway or through Kafka messages.

### Layer 3 — The Data Layer (where everything is stored)
Multiple databases — PostgreSQL for structured data, Redis for fast temporary data, MongoDB for notifications, Cassandra for the audit log.

---

## Why This Shape?

This is called a **microservices architecture**. The alternative is a **monolith** — one giant program that does everything.

| Monolith | Microservices |
|---|---|
| One program, easy to start | Many programs, complex to start |
| One bug can crash everything | One bug affects only one service |
| Hard to scale specific parts | Can scale only the busy parts |
| Easy to deploy | Need orchestration (Kubernetes) |
| Good for small teams | Good for large teams and large scale |

For a banking app handling millions of users, microservices is the right choice. But it comes with complexity — which is why this app also has extensive infrastructure to manage that complexity.
