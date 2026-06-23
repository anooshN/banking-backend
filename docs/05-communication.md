# 5. How Services Talk to Each Other

## Two Ways to Communicate

Services need to talk to each other. There are two fundamentally different ways they do this.

---

## Way 1 — Synchronous (Ask and Wait)

**Simple explanation:** You call someone on the phone. You wait on the line until they answer. You get the answer immediately.

This is used when:
- You need the answer **right now** to continue
- Example: Before debiting an account, you need to know the current balance

**Technology used: Feign Client (HTTP REST)**

```java
// This interface is all you write
// Feign generates the actual HTTP call code
@FeignClient(name = "account-service")
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountId}/balance")
    BigDecimal getBalance(@PathVariable UUID accountId);
}

// In TransactionService, you just call it like a regular method:
BigDecimal balance = accountServiceClient.getBalance(accountId);
// Feign automatically:
//   1. Looks up "account-service" address in Eureka
//   2. Makes HTTP GET request to that address
//   3. Parses the JSON response
//   4. Returns a BigDecimal
```

**The Danger:** What if account-service is down or slow? Your transaction-service call gets stuck waiting. This is why every synchronous call has a **circuit breaker**.

---

## Way 2 — Asynchronous (Leave a Message)

**Simple explanation:** You leave a note on someone's desk. They read it when they are ready. You don't wait around.

This is used when:
- You don't need the answer immediately
- Multiple services need to react to the same event
- You want decoupling — the sender doesn't need to know who the receivers are

**Technology used: Apache Kafka**

Example: When a transaction completes:
1. transaction-service publishes: "Transaction TXN123 for $500 on account ACC456 just completed"
2. notification-service reads it → sends you an SMS and push notification
3. audit-service reads it → writes an immutable audit log entry
4. fraud-detection-service reads it → scores it for fraud
5. report-service reads it → updates monthly totals

All of this happens **simultaneously** and transaction-service doesn't wait for any of it.

---

## Synchronous vs Asynchronous: When to Use Which

| Situation | Use | Why |
|---|---|---|
| Need result to continue processing | Synchronous (Feign) | Can't proceed without the answer |
| Just informing others of an event | Asynchronous (Kafka) | Don't need to wait |
| Multiple services need to react | Asynchronous (Kafka) | One message, many consumers |
| Single target, one direction | Either | Depends on timing needs |
| High throughput, don't want back-pressure | Asynchronous (Kafka) | Kafka buffers the load |

---

## The Header: X-Correlation-ID

**Simple explanation:** Every request gets a unique tracking number, like a FedEx tracking code. You can use this to trace a single user action across all the logs from all 14 services.

```
Browser → Gateway → auth-service → Kafka → notification-service

Each hop adds the SAME correlation ID to its logs:
[2024-01-15 10:23:45] [correlationId=abc-123] auth-service: User logged in
[2024-01-15 10:23:45] [correlationId=abc-123] notification-service: Sending login alert
```

Without this, debugging would require comparing timestamps across 14 log files hoping to match them up.

How it works:
1. Browser sends request without a Correlation ID
2. API Gateway generates one: `UUID.randomUUID()` → `abc-123-def-456`
3. Gateway adds it to the request headers: `X-Correlation-ID: abc-123-def-456`
4. Every service reads it, adds it to their logs via MDC (Mapped Diagnostic Context)
5. Every service passes it on to any services they call
