# 3. Microservices — What and Why

## The Simple Version

Imagine you have a big toy box with all your toys mixed together. Every time you want one toy, you have to dig through everything.

Now imagine you have **separate boxes** — one for LEGO, one for cars, one for dolls. Each box does one thing. If the LEGO box breaks, you can still play with the cars.

That is what microservices are. **Each service is its own small program that does one job.**

---

## What Is a Microservice?

A microservice is:
- A **separate program** that runs on its own
- Has its **own database** that no other service can directly access
- Talks to other services only through **defined interfaces** (APIs or messages)
- Can be **deployed independently** — update one without touching the others
- Can be **scaled independently** — run 10 copies of the busy one, only 2 of the quiet one

---

## The 14 Services in This App

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                       │
│                                                               │
│  discovery-server    config-server      api-gateway           │
│  (Eureka)           (Git-backed)        (Spring Cloud GW)     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     BUSINESS LAYER                            │
│                                                               │
│  auth-service        user-service       account-service       │
│  transaction-service payment-service    notification-service  │
│  audit-service       report-service     fraud-detection       │
│  card-service        loan-service                             │
└─────────────────────────────────────────────────────────────┘
```

---

## How Do Services Find Each Other?

**Problem:** If auth-service needs to call account-service, what address does it use? Services can start and stop, their IP addresses change.

**Solution:** A **service registry** (Eureka). Think of it like a phonebook.

1. When account-service starts, it tells Eureka: "I am account-service, I am at address X"
2. When auth-service wants to call account-service, it asks Eureka: "Where is account-service?"
3. Eureka replies: "It's at address X"
4. auth-service calls account-service at that address

If account-service crashes and a new one starts at address Y, Eureka updates automatically. The callers never need to be updated.

---

## What Happens When a Service Is Down?

This is called a **circuit breaker pattern** (like the circuit breaker in your house's electrical panel).

If account-service is down:
1. The first call fails → circuit breaker records the failure
2. After 5 failures in a row → the circuit "opens" (like a blown fuse)
3. For the next 10 seconds, calls immediately fail without even trying → this prevents overwhelming a struggling service
4. After 10 seconds, one test call is allowed through
5. If that works → circuit "closes" again and things go back to normal
6. While the circuit is open → a **fallback** response is returned ("Service unavailable, please try again")

This is implemented using **Resilience4j** in every service that calls another service.

---

## The Single Responsibility Principle

Each service does **one thing** and does it well:

- `auth-service` — ONLY handles login, logout, tokens. Nothing else.
- `account-service` — ONLY handles account balances and status. Nothing else.
- `transaction-service` — ONLY records and processes money movements. Nothing else.

If you need to add a new loan feature, you touch ONLY `loan-service`. The other 13 services don't care and don't change.
