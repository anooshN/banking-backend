# Spring Data JPA & Hibernate

## What Is JPA?

JPA = Java Persistence API. It is a standard for mapping Java objects to database tables.

**Simple explanation:** Instead of writing SQL like `INSERT INTO accounts (id, balance, user_id) VALUES (?, ?, ?)`, you just call `accountRepository.save(account)` and JPA writes the SQL for you.

Hibernate is the most popular JPA implementation. Spring Data JPA wraps Hibernate and adds even more convenience.

---

## Entity Mapping

Every database table has a corresponding Java class annotated with `@Entity`:

```java
// maps to the 'accounts' table in PostgreSQL
@Entity
@Table(name = "accounts",
       indexes = {
           @Index(name = "idx_accounts_user_id", columnList = "user_id"),
           @Index(name = "idx_accounts_status",  columnList = "status")
       })
@Data                // Lombok: generates getters, setters, equals, hashCode, toString
@Builder             // Lombok: generates builder pattern
@NoArgsConstructor   // Lombok: no-arg constructor (required by JPA)
@AllArgsConstructor  // Lombok: all-arg constructor (used by @Builder)
@EntityListeners(AuditingEntityListener.class)  // enables @CreatedDate, @LastModifiedDate
public class Account {

    @Id                              // marks this as the primary key
    @GeneratedValue(strategy = GenerationType.UUID)
    // GenerationType.UUID: the database generates UUIDs via uuid_generate_v4()
    private UUID id;

    @Column(name = "account_number", // maps to "account_number" column
            unique = true,           // adds UNIQUE constraint in DDL
            nullable = false)        // adds NOT NULL constraint
    private String accountNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;             // not a @ManyToOne — different database (microservice)
                                     // we store the ID, not a JPA relationship

    @Enumerated(EnumType.STRING)     // store enum as string ("CHECKING") not integer (0)
    // EnumType.ORDINAL (storing 0,1,2) is dangerous: adding enum values changes meanings
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(nullable = false,
            precision = 19,          // total digits
            scale = 4)               // digits after decimal point
    private BigDecimal balance;      // NUMERIC(19,4) in PostgreSQL — exact money storage

    @CreatedDate                     // automatically set by AuditingEntityListener
    @Column(name = "created_at",
            updatable = false)       // never update this column after first insert
    private LocalDateTime createdAt;

    @LastModifiedDate                // automatically updated by AuditingEntityListener
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AccountType  { CHECKING, SAVINGS, INVESTMENT }
    public enum AccountStatus { ACTIVE, INACTIVE, FROZEN, CLOSED }
}
```

---

## Repository Pattern

Spring Data JPA generates SQL from method names:

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // JpaRepository<T, ID> gives you for free:
    // save(entity), findById(id), findAll(), deleteById(id),
    // existsById(id), count(), saveAll(list), findAllById(list)

    // Spring Data generates SQL from method name:
    // SELECT * FROM accounts WHERE user_id = ?
    List<Account> findByUserId(UUID userId);

    // SELECT * FROM accounts WHERE account_number = ?
    Optional<Account> findByAccountNumber(String accountNumber);

    // SELECT * FROM accounts WHERE user_id = ? AND status = 'ACTIVE'
    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);

    // SELECT * FROM accounts WHERE balance > ?
    List<Account> findByBalanceGreaterThan(BigDecimal amount);

    // SELECT COUNT(*) FROM accounts WHERE user_id = ?
    long countByUserId(UUID userId);

    // Custom JPQL query (Java Persistence Query Language — database-independent SQL)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);

    // Native SQL when JPQL isn't enough
    @Query(value = "SELECT * FROM accounts WHERE balance > :amount AND created_at > :date",
           nativeQuery = true)
    List<Account> findRecentHighBalanceAccounts(
            @Param("amount") BigDecimal amount,
            @Param("date") LocalDateTime date);

    // EXISTS query — more efficient than count() > 0
    boolean existsByAccountNumber(String accountNumber);
}
```

---

## Transaction Management with JPA

```java
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    // @Transactional — ensures these operations are atomic
    @Transactional
    public Account updateBalance(UUID accountId, BigDecimal amount) {
        // findById within @Transactional = entity is "managed" by JPA
        Account account = accountRepository.findById(accountId).orElseThrow(...);

        account.setBalance(account.getBalance().add(amount));
        account.setAvailableBalance(account.getAvailableBalance().add(amount));

        // save() is optional here because:
        // Within a @Transactional method, managed entities are "dirty checked"
        // JPA detects changes and generates UPDATE automatically at commit
        // But it's good practice to call save() explicitly for clarity
        return accountRepository.save(account);
    }

    // readOnly = true: tells Hibernate not to track changes (faster reads)
    @Transactional(readOnly = true)
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
    }
}
```

---

## Flyway Database Migrations

**Problem:** How do you evolve your database schema (add columns, create tables) as the code evolves, across multiple environments and team members?

**Solution:** Flyway versioned migration scripts. Each script runs exactly once, in order.

```
src/main/resources/db/migration/
├── V1__create_accounts_table.sql      ← runs first, ever
├── V2__add_overdraft_column.sql       ← runs second, ever
├── V3__add_investment_type.sql        ← runs third, ever
└── V4__add_account_indexes.sql        ← runs fourth, ever
```

**Naming convention:**
- `V` = versioned migration
- `1` = version number (must be unique and increasing)
- `__` = double underscore separator
- `create_accounts_table` = description
- `.sql` = SQL file

**V1__create_accounts_table.sql:**
```sql
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number  VARCHAR(30)     UNIQUE NOT NULL,
    user_id         UUID            NOT NULL,
    account_type    VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    balance         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency_code   CHAR(3)         NOT NULL DEFAULT 'USD',
    routing_number  VARCHAR(20),
    interest_rate   NUMERIC(5,4),
    overdraft_limit NUMERIC(19,4)   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_accounts_user_id       ON accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status        ON accounts(status);
```

**V2__add_overdraft_column.sql** — example of adding a column later:
```sql
ALTER TABLE accounts ADD COLUMN overdraft_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN overdraft_interest_rate NUMERIC(5,4);

-- Update existing rows
UPDATE accounts SET overdraft_enabled = TRUE WHERE account_type = 'CHECKING';
```

**How Flyway tracks what's been run:**
Flyway creates a `flyway_schema_history` table in your database:
```
version | description              | checksum   | installed_on        | success
--------|--------------------------|------------|---------------------|--------
1       | create accounts table    | -123456789 | 2024-01-15 10:00:00 | true
2       | add overdraft column     | 987654321  | 2024-02-01 09:00:00 | true
```

If you try to run the app and V1 has already run (checksum matches), Flyway skips it. If V2 is new, it runs it.

---

## Auditing — @CreatedDate, @LastModifiedDate

**Automatically track who created and modified records:**

```java
// Enable on the Application class:
@SpringBootApplication
@EnableJpaAuditing
public class AccountServiceApplication { ... }

// In the entity:
@EntityListeners(AuditingEntityListener.class)
public class Account {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    // Set automatically when entity is first saved
    // updatable=false means it cannot be changed after that

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    // Updated automatically every time the entity is saved
}
```

**For tracking WHO (user) made the change, not just when:**
```java
// Configure auditor provider:
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return Optional.of(auth.getName()); // the userId from JWT
        }
        return Optional.of("system");
    };
}

// In entity:
@CreatedBy
@Column(name = "created_by", updatable = false)
private String createdBy;  // automatically set to current userId

@LastModifiedBy
@Column(name = "modified_by")
private String modifiedBy;
```

---

## N+1 Query Problem and How We Avoid It

**The N+1 problem:**
```java
// BAD — generates N+1 SQL queries
List<User> users = userRepository.findAll();  // 1 query: SELECT * FROM users
for (User user : users) {
    List<String> roles = user.getRoles();     // N queries: SELECT * FROM user_roles WHERE user_id = ?
    // For 1000 users = 1001 queries!
}

// GOOD — JOIN FETCH: 1 query total
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
Optional<User> findByEmailWithRoles(@Param("email") String email);
// Generates: SELECT u.*, r.* FROM users u LEFT JOIN user_roles r ON u.id = r.user_id
// 1 query, not 1+N
```

**Why this matters:** In the auth-service, we need to load a user AND their roles on every login. Without JOIN FETCH, that is 2 queries. At 1000 logins per second, that is 1000 extra queries per second = database overload.

---

## Pagination

```java
// Repository method with pagination:
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Pageable = page number + page size + sort
    Page<Transaction> findByAccountId(UUID accountId, Pageable pageable);
}

// Service — use PageRequest to create Pageable:
public PageResponse<Transaction> getTransactionHistory(UUID accountId, int page, int size) {
    Pageable pageable = PageRequest.of(
            page,                              // page number (0-based)
            size,                              // records per page
            Sort.by("createdAt").descending()  // newest first
    );
    Page<Transaction> transactionPage = transactionRepository.findByAccountId(accountId, pageable);
    // transactionPage contains:
    // - content (List<Transaction> for this page)
    // - totalElements (total matching records)
    // - totalPages
    // - hasNext / hasPrevious
    // - etc.

    return PageResponse.of(transactionPage);
}

// Controller:
@GetMapping("/account/{accountId}")
public ResponseEntity<ApiResponse<PageResponse<Transaction>>> getHistory(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,    // ?page=0
        @RequestParam(defaultValue = "20") int size) { // ?size=20
    return ResponseEntity.ok(ApiResponse.success(
            transactionService.getTransactionHistory(accountId, page, size)));
}
```

---

## Hikari Connection Pool

Every database call needs a connection. Creating a new connection each time is slow (~50ms). A connection pool maintains a set of ready connections.

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # max 20 simultaneous connections
      minimum-idle: 5             # always keep 5 idle connections ready
      connection-timeout: 30000   # wait max 30s to get a connection from pool
      idle-timeout: 600000        # remove idle connections after 10 minutes
      max-lifetime: 1800000       # replace connections after 30 minutes
                                  # (prevents using stale connections)
      leak-detection-threshold: 60000  # warn if connection not returned in 60s
```

**How it works:**
1. Service starts → Hikari creates 5 idle connections to PostgreSQL
2. Request arrives → Hikari gives it one of the 5 idle connections (instant)
3. Request finishes → connection returned to pool (not closed)
4. If all 5 are busy → Hikari creates up to 20 connections total
5. If 20 are all busy → new requests wait up to 30s (connection-timeout)
6. Idle connections above 5 are closed after 10 minutes
