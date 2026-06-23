# 4. Every Service Explained

## Infrastructure Services

---

### Discovery Server (Eureka) — Port 8761

**Simple explanation:** The phonebook. Every service registers its address here when it starts. Other services look up addresses here instead of hardcoding them.

**Why it exists:** In Kubernetes, pods (containers) get new IP addresses every time they restart. A hardcoded IP would break immediately. Eureka solves this.

**What it does technically:**
- Every service sends a "heartbeat" to Eureka every 30 seconds: "I'm still alive"
- If Eureka doesn't hear a heartbeat for 90 seconds, it removes the service from the registry
- Clients cache the registry locally so they still work even if Eureka goes down briefly

**Security:** Protected by HTTP Basic Auth (username/password). Without this, any program on the network could register itself as any service.

---

### Config Server — Port 8888

**Simple explanation:** The settings book. Instead of having configuration (database passwords, Kafka addresses, etc.) scattered in 14 different places, they all live in one Git repository. The Config Server reads that Git repo and hands out the right settings to each service.

**Why it exists:** Imagine changing the Redis server address. Without a config server, you would update 14 files and redeploy all 14 services. With a config server, you change one file in Git and all services pick it up automatically.

**What it does technically:**
- Backed by a Git repository (`banking-config` repo)
- Values can be **encrypted** using a symmetric key — passwords in Git are stored encrypted, decrypted only at runtime
- Services fetch their config at startup via: `http://config-server:8888/{service-name}/{profile}`
- Supports profiles: `dev`, `test`, `prod` — same service gets different settings per environment

---

### API Gateway — Port 8080

**Simple explanation:** The front door. Every single request from the outside world comes through here first. The gateway decides whether to let it in, which service to send it to, and whether to slow it down if there are too many requests.

**Why it exists:** Without a gateway:
- Every service would need its own security logic
- The frontend would need to know the address of every service
- There would be no central place to add rate limiting or logging

**What it does technically:**

```
Request from browser
        │
        ▼
┌──────────────────┐
│  CORS Check      │  Is this request from an allowed website?
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  JWT Validation  │  Does the request have a valid login token?
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  Rate Limiter    │  Has this user sent too many requests? (uses Redis)
│  (Redis-backed)  │  Default: 10 req/s per user, burst up to 20
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  Route to        │  /api/v1/accounts/** → account-service
│  correct service │  /api/v1/auth/**    → auth-service
└──────────────────┘
        │
        ▼
┌──────────────────┐
│  Circuit Breaker │  If service is down → return fallback response
└──────────────────┘
```

**Public paths (no token needed):**
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `/actuator/**` (health checks for Kubernetes)
- `/swagger-ui/**` (API documentation)

---

## Business Services

---

### Auth Service — Port 8081

**Simple explanation:** The bouncer at the door. You prove who you are (email + password), and the bouncer gives you a wristband (JWT token) that lets you in.

**What it does:**

**Register:**
1. Check if email already exists → reject if yes
2. Hash the password using BCrypt (so even database admins can't see it)
3. Save user to PostgreSQL
4. Generate access token (valid 15 minutes) + refresh token (valid 24 hours)
5. Return both tokens

**Login:**
1. Find user by email
2. Check if account is locked (too many failed attempts) → reject if yes
3. Compare submitted password against stored hash → reject if wrong
4. Increment failed attempt counter if wrong
5. Lock account for 30 minutes after 5 failures
6. On success: generate new tokens, store refresh token in Redis

**Token Refresh:**
1. Validate the refresh token signature
2. Check refresh token exists in Redis (not yet used or invalidated)
3. Delete old refresh token from Redis (rotation — prevents reuse)
4. Generate new access + refresh token pair

**Logout:**
1. Add the access token to Redis blacklist (expires in 1 hour)
2. The API Gateway checks this blacklist on every request

**Database:** Separate PostgreSQL instance (`banking_auth`). `users` table + `user_roles` table.

**Why separate DB?** Security isolation. Even if the accounts database is compromised, the password hashes are in a different database with different credentials.

---

### User Service — Port 8082

**Simple explanation:** Your profile page. Everything that describes YOU as a person — your name, address, date of birth, identity documents.

**What it does:**
- Stores personal information (first name, last name, address, phone)
- Handles KYC (Know Your Customer) — identity verification required by banking regulations
- KYC states: `PENDING → SUBMITTED → VERIFIED → REJECTED`
- Preferred language and currency settings

**Why separate from auth-service?** Auth-service knows your email and password. User-service knows your personal details. If a hacker gets into user-service, they get your name and address but not your login credentials. Separation limits the damage.

---

### Account Service — Port 8083

**Simple explanation:** Your piggy bank. Knows how much money is in each of your accounts.

**What it does:**
- Creates accounts (CHECKING, SAVINGS, INVESTMENT)
- Tracks balance and available balance (balance minus holds)
- Freezes accounts (fraud/compliance action)
- Emits Kafka events when accounts change

**Caching with Redis:**
Every time someone asks "what is the balance of account X?" the answer is stored in Redis for 5 minutes. The next person asking the same question gets the answer instantly from Redis instead of hitting the database.

```
Request for account balance
        │
        ▼
┌──────────────────┐
│ Check Redis      │── Found ──→ Return immediately (< 1ms)
└──────────────────┘
        │ Not found
        ▼
┌──────────────────┐
│ Query PostgreSQL │── Store result in Redis ──→ Return (~5ms)
└──────────────────┘
```

**@Auditable annotation:** Every method that changes an account is decorated with `@Auditable`. An AOP aspect intercepts the call and automatically publishes an audit event to Kafka. The developer writing the business logic doesn't need to think about auditing — it happens automatically.

---

### Transaction Service — Port 8084

**Simple explanation:** The cashier's receipt book. Every time money moves, this service writes it down.

**What it does:**
- Records every debit (money leaving) and credit (money arriving)
- Validates that the account has enough money before debiting
- Publishes Kafka events for each transaction (other services react to these)
- Stores the balance before and after each transaction (for audit trail)

**CQRS (Command Query Responsibility Segregation):**

This is a fancy way of saying "reads and writes are separated."

Simple explanation: In a library, there are people who ADD books (writes) and people who READ books (reads). If they share the same checkout desk, reads slow down writes and vice versa. So you have a separate "additions" desk and a "reading room."

In the transaction service:
- **Commands** (writes): POST /transactions → recorded immediately, triggers Kafka events
- **Queries** (reads): GET /transactions/account/{id} → can be served from a read replica of the database

**Feign Client to Account Service:**
Before debiting, transaction-service calls account-service to check the balance:

```java
@FeignClient(name = "account-service")
public interface AccountServiceClient {
    @GetMapping("/api/v1/accounts/{id}/balance")
    BigDecimal getBalance(@PathVariable UUID id);
}
```

Feign generates all the HTTP call code automatically. You just write an interface.

**Circuit Breaker on the Feign Call:**
If account-service is down, the circuit breaker triggers `AccountServiceClientFallback` which throws a `SERVICE_UNAVAILABLE` exception, preventing the transaction from going through with unverified balance.

---

### Payment Service — Port 8085

**Simple explanation:** The wire transfer department. Sending money to other banks, not just internally.

**Payment Rails (how money travels):**

| Rail | Use Case | Speed | Example |
|---|---|---|---|
| INTERNAL | Within our bank | Instant | Transfer between your own accounts |
| ACH | US domestic, low value | 1-3 days | Direct deposit, bill pay |
| FEDWIRE | US domestic, high value | Same day | Real estate closing |
| CHIPS | US domestic, large banks | Same day | Interbank settlements |
| SWIFT | International | 1-5 days | Sending money abroad (MT103 message) |

**The Outbox Pattern (very important):**

**Problem:** After saving a payment to the database, what if the server crashes before publishing the Kafka event? The payment is saved but nobody knows about it. Money appears to disappear.

**Solution:** The Outbox Pattern.

Simple explanation: Instead of shouting the message directly, you write a note in a special "outbox" folder. A separate worker periodically checks the outbox and shouts the messages for you.

In code:
1. Save payment to `payments` table with `outbox_processed = false`
2. A `@Scheduled` job runs every 5 seconds
3. It finds all payments where `outbox_processed = false`
4. It publishes each one to Kafka
5. On success, sets `outbox_processed = true`

Now if the server crashes between step 1 and step 4, the payment is still in the database with `outbox_processed = false`. When the server restarts, the job picks it up and publishes it. No message is ever lost.

**SWIFT MT103 Message:**
For international payments, SWIFT defines a standard message format called MT103. The payment service builds this automatically:
```
:20:SWI1234567890        (transaction reference)
:32A:2024USD5000.00      (date, currency, amount)
:59:Jane Smith           (beneficiary name)
```

---

### Notification Service — Port 8086

**Simple explanation:** The person who taps you on the shoulder and says "hey, your salary just arrived!" Available via email, SMS, and real-time in-app alerts.

**What it does:**
- Listens to Kafka topics for events (transactions, payments, logins, fraud alerts)
- Creates notification records in MongoDB
- Pushes real-time notifications via WebSocket (STOMP protocol)
- Sends emails via JavaMail (SMTP)

**Why MongoDB?**
Notifications are not structured data. Some have images, some have action buttons, some have metadata. MongoDB's flexible document model handles this better than a rigid SQL table.

**WebSocket / STOMP:**
When you are logged into the banking app, your browser maintains a persistent connection to the notification service. When a new notification arrives for you, it is pushed instantly to your browser without you needing to refresh the page.

```
Browser ←──── persistent WebSocket connection ────→ Notification Service
                                                              │
                                    Kafka event arrives ──────┘
                                    "Your account was credited $5,000"
                                              │
                                    Saves to MongoDB
                                    Pushes to browser via WebSocket
                                    Sends email
```

---

### Audit Service — Port 8087

**Simple explanation:** The security camera that records everything forever and can never be deleted.

**Why Cassandra?**

Audit logs have a specific access pattern:
- You almost always query by user ID ("show me everything user X did")
- Within that, you want the most recent events first (time ordering)
- You write millions of records per day
- You almost never delete anything (legal requirement — 7 years retention in many countries)

Cassandra is designed exactly for this: it writes extremely fast, partitions data by a key (user_id), and clusters within each partition by time.

**The Cassandra Schema:**
```sql
CREATE TABLE audit_logs (
    user_id TEXT,           -- partition key: all events for a user go together
    event_time TIMESTAMP,   -- clustering key: sorted within a user's partition
    event_id UUID,          -- uniqueness
    action TEXT,
    resource TEXT,
    status TEXT,
    ...
    PRIMARY KEY ((user_id), event_time, event_id)
)
```

**Immutability:** Cassandra records are never updated after being written. This is critical for compliance — an audit log that can be changed is not a trustworthy audit log.

---

### Report Service — Port 8088

**Simple explanation:** The person who puts together your monthly statement PDF and emails it to you.

**Spring Batch:**
Generating a statement for a large account might involve processing 10,000 transactions. You cannot do this in a single HTTP request (it would time out).

Spring Batch breaks the job into **chunks**:
1. **Step 1 — Fetch:** Read transactions in pages of 100 from the transaction service
2. **Step 2 — Generate:** Write them into a PDF/CSV/Excel file
3. **Step 3 — Upload:** Upload the file to AWS S3
4. **Return:** A presigned URL the user can download

If step 2 fails halfway through, Spring Batch can **restart from the checkpoint** — it doesn't re-fetch the transactions, it continues from page 50 of 100.

**Scheduled Monthly Statements:**
`@Scheduled(cron = "0 0 1 1 * *")` — this runs at 1:00 AM on the 1st of every month. It generates statements for all active accounts automatically.

---

### Fraud Detection Service — Port 8089

**Simple explanation:** The suspicious person detector. Watches every transaction and scores how suspicious it looks.

**Rules Engine:**

| Rule | Score Impact | Reason |
|---|---|---|
| Transaction > $10,000 | +0.30 | High-value transactions are more likely to be fraud |
| More than 20 transactions in 1 hour | +0.50 | Nobody makes 20 transactions in an hour normally |
| New IP address | +0.10 | Could be a stolen account used from another location |

**Risk Levels:**
- 0.0 – 0.29 → LOW (normal transaction)
- 0.30 – 0.49 → MEDIUM (flag for review)
- 0.50 – 0.79 → HIGH (alert sent, transaction may be blocked)
- 0.80 – 1.00 → CRITICAL (transaction blocked immediately)

**Redis for Velocity Tracking:**
For the "20 transactions in 1 hour" check, the service increments a counter in Redis:
- Key: `fraud:velocity:{userId}`
- Value: transaction count
- Expiry: 60 minutes

Redis is used because it is extremely fast (microseconds) and the counter automatically disappears after 60 minutes. A database query would be too slow for every transaction.

**IP Fingerprinting:**
When you log in from a new IP address, the service flags it. The key is stored in Redis for 30 days. If you use the same IP within 30 days, no flag. New IP → small score increase.

---

### Card Service — Port 8090

**Simple explanation:** Manages your bank card. You can see it, block it if lost, set limits.

**What it stores:**
- Card number is stored **masked** (`**** **** **** 4242`) and **hashed** — the actual card number is never stored in plaintext
- Expiry date, cardholder name, card type (DEBIT/CREDIT/VIRTUAL)
- Controls: international transactions on/off, contactless on/off, online payments on/off
- Limits: daily limit, monthly limit

**Why mask the card number?**
PCI-DSS compliance. Even if the database is stolen, the attacker cannot use masked card numbers for fraud.

---

### Loan Service — Port 8091

**Simple explanation:** The loan department. Apply for a loan, see how much you owe, track your monthly payments.

**EMI Calculation:**
EMI = Equated Monthly Installment — the fixed amount you pay every month.

Formula:
```
EMI = P × r × (1+r)^n / ((1+r)^n - 1)

Where:
  P = Principal (the amount you borrowed)
  r = Monthly interest rate (annual rate ÷ 12 ÷ 100)
  n = Number of months
```

Example: Borrow $10,000 at 9.5% per year for 36 months
- r = 9.5 / 12 / 100 = 0.00792
- EMI = 10000 × 0.00792 × (1.00792)^36 / ((1.00792)^36 - 1)
- EMI = **$320.34 per month**

The loan service calculates this automatically when you apply.
