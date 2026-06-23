# 7. Databases — Why So Many?

## The Simple Version

Different jobs need different tools.

A hammer is great for nails. A screwdriver is great for screws. You wouldn't use a hammer for a screw just because you already have a hammer.

Different types of data have different needs. That's why this app uses 5 different databases.

---

## Database-per-Service Pattern

**Rule:** Each service owns its data. No other service can directly query another service's database.

Why?

1. **Independence:** account-service can change its database schema without breaking transaction-service
2. **Security:** If transaction-service is compromised, the attacker cannot directly access auth-service's password hashes
3. **Technology choice:** Each service can use the best database for its needs

If transaction-service needs data from account-service, it calls the account-service **API**. It never queries the accounts database directly.

---

## PostgreSQL — The Workhorse (4 instances)

**Simple explanation:** A highly organized filing cabinet. Perfect for structured data with relationships between records.

Used by: auth-service, account-service, transaction-service, payment-service

**Why PostgreSQL?**
- **ACID transactions:** Atomicity, Consistency, Isolation, Durability. When you transfer money, either BOTH the debit and credit happen, or NEITHER does. No in-between state.
- **Rich querying:** Complex SQL queries for reports, searching, filtering
- **Foreign keys:** Ensures data integrity (you cannot create a transaction for an account that doesn't exist)
- **Mature:** 35 years old, battle-tested in banking systems worldwide

**Our Tables:**

auth-service:
- `users` — email, hashed password, status, MFA settings
- `user_roles` — which roles each user has

account-service:
- `accounts` — account number, balance, type, status

transaction-service:
- `transactions` — every debit and credit ever made

payment-service:
- `payments` — payment status, rail, amounts, outbox flag

---

## Redis — The Speed Layer

**Simple explanation:** A whiteboard on the wall. You write things there when you need to look them up instantly. It's fast because it's always in memory (RAM), not on disk.

Used by: API Gateway (rate limiting), auth-service (token blacklist, refresh tokens), account-service (balance cache), fraud-detection (velocity counters, IP tracking)

**Why Redis?**
- **Sub-millisecond response:** 100x faster than PostgreSQL for simple lookups
- **Automatic expiry:** Set a TTL (time to live), data self-deletes. Perfect for tokens that should expire.
- **Atomic operations:** `INCR` (increment) is atomic — perfect for rate limiting and velocity checking
- **Data structures:** Not just key-value, but also lists, sets, sorted sets — used for rate limiting queues

**What's stored:**

| Key Pattern | Value | TTL | Purpose |
|---|---|---|---|
| `blacklist:{token}` | userId | 1 hour | Logged-out tokens |
| `refresh:{token}` | userId | 24 hours | Valid refresh tokens |
| `account:{id}` | Account JSON | 5 minutes | Balance cache |
| `fraud:velocity:{userId}` | Count | 1 hour | Transaction rate |
| `fraud:ip:{userId}:{ip}` | "1" | 30 days | Known IPs |
| Rate limit bucket | Token count | Rolling | API rate limiting |

**Why not just use PostgreSQL for everything?**

Checking the token blacklist happens on EVERY request. With thousands of requests per second, that's thousands of database queries per second just for that one check. Redis handles this in microseconds. PostgreSQL would become a bottleneck.

---

## MongoDB — The Flexible Store

**Simple explanation:** A box where you can throw in any shaped item. Don't need to pre-define exactly what shape each item will be.

Used by: notification-service

**Why MongoDB for notifications?**

Notifications can look very different:
```json
// Transaction notification
{ "type": "TRANSACTION", "amount": 500, "description": "Salary", "icon": "💰" }

// Fraud alert
{ "type": "FRAUD", "location": "Lagos", "device": "Unknown Android", "actionUrl": "/security" }

// Loan reminder  
{ "type": "LOAN", "dueDate": "2024-02-01", "amount": 320.34, "penaltyIfMissed": 50 }
```

In PostgreSQL, you would need a rigid table schema. Some columns would be NULL for most notification types. Or you'd need complex joins.

In MongoDB, each notification is a document and can have different fields. No NULL columns, no wasted space, no schema migrations every time you add a new notification type.

**Access pattern:** Almost always "give me all notifications for user X, newest first." MongoDB's flexible indexing handles this perfectly.

---

## Cassandra — The Audit Database

**Simple explanation:** A write-only filing system optimized for "give me everything this person did, in chronological order."

Used by: audit-service

**Why Cassandra for audit logs?**

Audit logs have a very specific access pattern:
- **Write:** Millions of small writes per day (every action generates an audit event)
- **Read:** "Show me all actions by user X between date A and date B"
- **Never delete:** Legal compliance requires 7 years of retention

Cassandra is designed exactly for this:
- **Writes are extremely fast** — no locking, no complex B-tree updates
- **Partition by user_id** — all data for a user is co-located on the same node
- **Cluster by time** — data is automatically sorted by timestamp
- **Linear scalability** — add more nodes for more throughput, no complex resharding

```
Partition Key: user_id → "all events for this user are on ONE node"
Clustering Key: event_time → "sorted by time within that node"
```

A query like "give me user ABC's last 100 events" is extremely fast because:
1. Cassandra knows exactly which node has user ABC's data (partition key)
2. The data is already sorted by time on that node (clustering key)
3. Just return the last 100 rows

In PostgreSQL, the same query needs an index scan, potentially across multiple nodes with joins.

---

## S3 — Document Storage

**Simple explanation:** A giant, unlimited file folder in the cloud. Store files, retrieve them by name.

Used by: report-service

Stores: Generated PDF statements, CSV exports, Excel reports

**Why S3, not a database?**
- PDFs can be megabytes in size
- Storing large binary files in a database is inefficient
- S3 can serve files directly with a presigned URL — the user downloads directly from S3, not through our servers

**Presigned URLs:**
A temporary URL that gives access to a specific file for a limited time (e.g., 1 hour). After that, the URL expires and the file is inaccessible via that link. This is more secure than storing files on a public server.

---

## Summary

| Database | Used By | Best For | Why Not Just PostgreSQL? |
|---|---|---|---|
| PostgreSQL | auth, accounts, transactions, payments | Structured data with ACID | This IS PostgreSQL |
| Redis | Gateway, auth, accounts, fraud | Speed, expiry, counters | PostgreSQL too slow for hot data |
| MongoDB | notifications | Flexible schemas | Notifications have varying shapes |
| Cassandra | audit | Massive writes, time-ordered reads | PostgreSQL can't scale writes this fast |
| S3 | reports | Large files | DBs not designed for large binary files |
