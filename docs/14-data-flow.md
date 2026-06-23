# 14. Data Flow — A Transaction Start to Finish

## The Complete Journey of a $500 Transfer

Let's trace exactly what happens when a user sends $500 to a friend.

---

## Step 0 — User Opens the App

1. Browser downloads React app from CloudFront (nearest AWS edge location)
2. App reads accessToken from localStorage → user is logged in → show Dashboard
3. RTK Query fetches accounts: GET /api/v1/accounts/user/{userId}
4. Request: Browser → API Gateway → account-service
5. account-service: Redis cache miss → query PostgreSQL → store in Redis → return accounts
6. Dashboard shows balances

---

## Step 1 — User Submits Transfer Form

User enters: receiver account ACC9876, amount $500, rail INTERNAL, description "Rent".

React Hook Form validates locally (required fields, amount > 0). Axios sends:
```
POST /api/v1/payments/initiate
Authorization: Bearer eyJhbGci...
{ "senderAccountId": "acc-001", "amount": 500, "paymentRail": "INTERNAL" }
```

---

## Step 2 — API Gateway Processing

1. CORS check: from localhost:5173? Yes → proceed
2. JWT validation: valid signature? not expired? not blacklisted in Redis? Yes → proceed
3. Extract userId from JWT, add X-User-Id header
4. Rate limit check: under 10 req/s? Yes → proceed
5. Route /api/v1/payments/** → payment-service (via Eureka load balancing)

---

## Step 3 — Payment Service

1. Role check: ROLE_CUSTOMER can initiate payments → proceed
2. Build Payment object with outbox_processed = false
3. Database transaction begins → save to PostgreSQL → commit
4. AOP Audit Aspect fires: publish to banking.audit.events
5. Return response to browser: "INITIATED" ← browser shows success

---

## Step 4 — Outbox Processor (5 seconds later)

Scheduled job finds outbox_processed = false payments → publishes to banking.payment.events Kafka topic → sets outbox_processed = true.

---

## Step 5 — Three Services React in Parallel

**transaction-service (Saga):**
1. Reads payment event from Kafka
2. Feign call to account-service: check sender balance ($15,250.75)
3. Balance > $500 → proceed
4. Create DEBIT transaction for sender (-$500) → publish to banking.transaction.events
5. Create CREDIT transaction for receiver (+$500)
6. Update both account balances in account-service
7. Evict both accounts from Redis cache
8. Ack Kafka message

**audit-service:**
1. Reads payment event from Kafka
2. Write immutable record to Cassandra (userId, action, amount, timestamp)
3. Ack Kafka message

**fraud-detection-service:**
1. Reads payment event from Kafka
2. Score: $500 < $10k (no flag), velocity count = 2 (no flag), known IP (no flag) → score 0.05 → LOW risk
3. No alert. Ack Kafka message.

---

## Step 6 — Notification Service Reacts

transaction-service published to banking.transaction.events.

1. notification-service reads event from Kafka
2. Creates notification in MongoDB: "You sent $500 to ACC987..."
3. Pushes to browser via WebSocket: bell badge increments to 1
4. Sends email to john@banking.com

---

## Total Time

- Steps 1-3 (user is waiting): ~50-150ms
- Steps 4-6 (happen after response sent): ~100-500ms

From user's perspective: clicked button, saw "Transfer Initiated!" in under 200ms.
From system's perspective: 6 services, 4 databases, 2 Kafka topics, all in under a second, all durable, all audited.

---

## What If Something Goes Wrong?

**Scenario:** transaction-service crashes halfway through Step 5.

The Kafka message was read but acknowledge() was never called. When transaction-service restarts, Kafka redelivers the message.

transaction-service processes it again. Did it already debit? The DEBIT transaction has a unique referenceNumber. If it tries to create a duplicate, the database unique constraint rejects it. transaction-service detects the duplicate and skips to the next step.

This is **idempotent processing** — safe to process the same message multiple times with the same final result.
