# 15. Design Patterns Used

A design pattern is a proven solution to a common problem — like a recipe instead of figuring out from scratch every time.

---

## 1. Saga Pattern (Distributed Transactions)

**Problem:** A money transfer touches 2 accounts in different services with different databases. You can't use a single database transaction.

**Solution:** Break into steps. Each step publishes an event when complete. The next step listens for that event.

```
PAYMENT_INITIATED → DEBIT_COMPLETED → CREDIT_COMPLETED → NOTIFICATION_SENT
```

If any step fails, a compensating transaction runs in reverse to undo previous steps.

**Simple analogy:** When you order at a restaurant: waiter takes order (step 1), kitchen cooks (step 2), food plated (step 3), delivered (step 4). If kitchen burns the food (step 3 fails), waiter cancels your order (compensation).

---

## 2. Outbox Pattern (Guaranteed Message Delivery)

**Problem:** Save to database AND publish to Kafka. If server crashes between the two, events are lost.

**Solution:** Save "intent to publish" IN the same database transaction. A separate scheduled process publishes and marks done. The database save and the intent are atomic. The publish is eventually guaranteed.

---

## 3. Circuit Breaker

**Problem:** If account-service is down, transaction-service keeps waiting for 30-second timeouts. Under load, this fills all threads — transaction-service also crashes.

**Solution:** After N failures, stop trying. Return error immediately. Try again after cooldown.

```
CLOSED (normal) → too many failures → OPEN (stop trying)
OPEN → after 10 seconds → HALF-OPEN (test one request)
HALF-OPEN → success → CLOSED | failure → OPEN
```

---

## 4. CQRS (Command Query Responsibility Segregation)

**Problem:** Reads and writes have different requirements. Reads should be fast. Writes must be consistent.

**Solution:** Separate read model from write model.

- Command (write): POST /transactions → writes to primary DB, publishes events
- Query (read): GET /transactions/account/{id} → reads from potentially a read replica

**Simple analogy:** A library has separate desks for adding books (writes) and reading books (reads). They don't compete for the same resources.

---

## 5. Event Sourcing

**Problem:** Traditional databases store current state. But how did it get there? What if there's a dispute?

**Solution:** Store events, not state. "Account created. Deposited $5,000. Withdrew $2,000." Current balance is derived from replaying events.

Every transaction record IS the event source. The balance is always auditable.

---

## 6. Repository Pattern

**Problem:** Business logic shouldn't care how data is stored. What if you switch databases?

**Solution:** A Repository interface hides database details. Business logic calls the interface. The implementation details are separate.

```java
// Business logic calls this interface — doesn't know or care about SQL
public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);
}
// Spring Data JPA generates the SQL automatically
```

---

## 7. Builder Pattern

**Problem:** Creating complex objects with many parameters. Easy to get the order wrong.

**Solution:** Chain method calls to set only what you need. Lombok's @Builder generates this code automatically.

```java
Account account = Account.builder()
    .userId(userId)
    .accountType(AccountType.CHECKING)
    .balance(BigDecimal.ZERO)
    .build();
```

---

## 8. AOP / Decorator Pattern

**Problem:** Every method that modifies data needs audit logging. You'd write logging code in hundreds of places.

**Solution:** Write audit logic once in an Aspect. Annotate any method with @Auditable. The framework intercepts the call and runs audit code automatically.

```java
@Auditable(action = "DEBIT", resource = "Transaction")
public Transaction debit(UUID accountId, BigDecimal amount) {
    // just business logic — audit happens automatically around it
}
```

Spring's @Cacheable and @CacheEvict use the same AOP mechanism for caching.

---

## 9. Anti-Corruption Layer

**Problem:** External systems (SWIFT, credit bureaus) have their own data formats that don't match ours.

**Solution:** A translation layer converts between our internal model and the external system's format.

```java
// Our internal model gets translated to SWIFT MT103 format
// The rest of our code never knows SWIFT format exists
private String buildSwiftMT103(Payment payment) {
    return String.format(":20:%s:32A:%s%s%.2f:59:%s", ...);
}
```

---

## 10. Database-per-Service

**Problem:** In a monolith, all services share one database. A schema change for one feature breaks all other features.

**Solution:** Each service owns its database. No other service can directly query it. Data flows through service APIs only.

Benefits: Independent deployments, technology freedom (each service picks best database for its needs), security isolation, independent scaling.
