# PostgreSQL

## Why PostgreSQL?

PostgreSQL is the primary database for 4 of our 14 services. It's a 35-year-old open-source relational database — battle-tested in banking systems worldwide.

**Chosen because:**
- ACID transactions (critical for money)
- NUMERIC type for exact decimal storage (no floating point errors)
- UUID support with `uuid_generate_v4()`
- Rich indexing (B-tree, partial, composite)
- Row-level locking for concurrent updates
- JSON support when needed

---

## ACID Transactions — Why Money Needs This

**ACID = Atomicity, Consistency, Isolation, Durability**

**Atomicity:** All or nothing. If you debit account A and credit account B in one transaction, either BOTH happen or NEITHER happens. No half-transfers.

```sql
BEGIN;
  UPDATE accounts SET balance = balance - 500 WHERE id = 'acc-A';  -- debit
  UPDATE accounts SET balance = balance + 500 WHERE id = 'acc-B';  -- credit
COMMIT;
-- If anything fails between BEGIN and COMMIT: ROLLBACK (both updates undone)
```

**Consistency:** Database constraints are never violated. You can't create a transaction for an account that doesn't exist (foreign key would prevent it).

**Isolation:** Concurrent transactions don't interfere. If two people withdraw from the same account simultaneously, they see a consistent balance and don't overdraft.

**Durability:** Once committed, data survives server restarts. Written to disk before COMMIT returns.

---

## Our Database Schema — Deep Dive

### accounts table (account-service)

```sql
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- uuid_generate_v4() generates a random UUID
    -- No sequential integer IDs — UUIDs are not guessable (security)

    account_number  VARCHAR(30)     UNIQUE NOT NULL,
    -- UNIQUE constraint creates an implicit B-tree index
    -- NOT NULL at database level (belt + suspenders with JPA @Column(nullable=false))

    user_id         UUID            NOT NULL,
    -- Stores UUID from auth-service — no foreign key (different database)
    -- Database-per-service means NO cross-service foreign keys

    account_type    VARCHAR(20)     NOT NULL CHECK (account_type IN ('CHECKING','SAVINGS','INVESTMENT')),
    -- CHECK constraint at DB level — even bypassing JPA, invalid types rejected

    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    -- DEFAULT 'ACTIVE' — new accounts start active without needing to set it in code

    balance         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    -- NUMERIC(19,4): exact decimal, no floating point errors
    -- 19 digits total, 4 after decimal: supports up to $999,999,999,999,999.9999
    -- NOT FLOAT or DOUBLE — those have rounding errors

    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    -- Separate from balance: balance minus holds (pending transactions)

    currency_code   CHAR(3)         NOT NULL DEFAULT 'USD',
    -- ISO 4217 currency code: exactly 3 characters

    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indexes for query patterns (crucial for performance at scale)
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
-- SELECT * FROM accounts WHERE user_id = ? → uses this index

CREATE INDEX idx_accounts_account_number ON accounts(account_number);
-- Used in payment routing (find receiver account)

CREATE INDEX idx_accounts_status ON accounts(status);
-- Partial index for faster queries: SELECT * FROM accounts WHERE status = 'ACTIVE'
```

### transactions table (transaction-service)

```sql
CREATE TABLE transactions (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(50)    UNIQUE NOT NULL,
    -- Unique reference for idempotency — prevent duplicate transactions

    account_id      UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    transaction_type VARCHAR(20)    NOT NULL,
    -- 'DEBIT', 'CREDIT', 'TRANSFER_OUT', 'TRANSFER_IN'

    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    amount          NUMERIC(19,4)   NOT NULL,
    currency_code   CHAR(3)         NOT NULL DEFAULT 'USD',

    balance_before  NUMERIC(19,4),  -- snapshot of balance BEFORE this transaction
    balance_after   NUMERIC(19,4),  -- snapshot of balance AFTER this transaction
    -- These create a complete audit trail without needing to replay all transactions

    counterparty_account VARCHAR(50),  -- who sent/received money
    counterparty_name    VARCHAR(255),
    description     TEXT,
    payment_rail    VARCHAR(20),       -- SWIFT, ACH, INTERNAL, etc.
    correlation_id  VARCHAR(100),

    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
    -- No updated_at: transactions are immutable once created (append-only)
);

-- Composite index: most queries filter by account_id AND sort by date
CREATE INDEX idx_transactions_account_date ON transactions(account_id, created_at DESC);

-- For idempotency check: ensure reference_number is unique
-- (implicit from UNIQUE constraint, but explicit index for fast lookup)
CREATE INDEX idx_transactions_reference ON transactions(reference_number);
```

---

## Aurora PostgreSQL in Production

In production, we use **Amazon Aurora PostgreSQL** instead of vanilla PostgreSQL:

| Feature | PostgreSQL | Aurora PostgreSQL |
|---|---|---|
| Setup | Self-managed | Fully managed by AWS |
| Storage | Single disk | Distributed across 3 AZs, 6 copies |
| Failover | Manual | Automatic (<30s) |
| Read scaling | Manual replicas | Up to 15 read replicas |
| Backups | Manual setup | Automatic, point-in-time recovery |
| Monitoring | Self-setup | CloudWatch built-in |

**Our Aurora config:**
```hcl
# terraform/rds.tf
resource "aws_rds_cluster" "accounts" {
  engine             = "aurora-postgresql"
  engine_version     = "15.4"
  database_name      = "banking_accounts"

  storage_encrypted      = true  # AES-256 encryption at rest
  deletion_protection    = true  # can't delete accidentally
  backup_retention_period = 7    # 7 days of automatic backups
  # Point-in-time recovery: restore to any second in the last 7 days
}
```

---

## Connection Pooling (HikariCP)

Without connection pooling: every query creates a new TCP connection to PostgreSQL (~50ms overhead). At 1000 queries/second = 50,000ms of connection overhead per second.

With Hikari: connections are reused. That 50ms overhead happens once at startup, not per query.

```yaml
spring.datasource.hikari:
  maximum-pool-size: 20        # max connections to PostgreSQL
  minimum-idle: 5              # keep 5 connections warm
  connection-timeout: 30000    # wait 30s for a free connection before failing
  idle-timeout: 600000         # close connections idle for 10+ minutes
  max-lifetime: 1800000        # replace connections every 30 minutes
                               # prevents using stale connections after network drops
  leak-detection-threshold: 60000  # warn if connection held for 60+ seconds
```

**Pool sizing rule of thumb:** `max-pool-size = (number of CPU cores × 2) + number of disks`
For a 4-core RDS instance: 4×2+1 = 9 connections per service instance.
With 3 service instances: 9×3 = 27 total connections.
Our limit of 20 per service matches this for a 2-core instance.
