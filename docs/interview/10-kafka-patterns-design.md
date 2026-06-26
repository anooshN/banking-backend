# Module 10 — Kafka, Patterns & Design

---

## Apache Kafka — Interview Deep Dive

### Why Kafka Over REST for Events

**Situation:**
When a transaction completes, 4 services need to react: notification-service (SMS/email), audit-service (immutable log), fraud-detection (scoring), report-service (monthly totals update). If transaction-service makes 4 synchronous REST calls, it takes 400-800ms extra and fails if any downstream service is down.

**Task:**
Decouple transaction processing from downstream reactions using event-driven architecture.

**Action:**

```java
// services/transaction-service — TransactionService.java
// AFTER saving transaction, publish ONE event:
eventProducer.publishEvent(
    "banking.transaction.events",
    txn.getId().toString(),
    "DEBIT_INITIATED:" + txn.getReferenceNumber()
);
// Transaction service returns in 50ms — doesn't wait for 4 downstream services

// 4 services consume the SAME event independently:
// notification-service group → reads it, sends SMS
// audit-service group → reads it, writes to Cassandra
// fraud-detection group → reads it, scores transaction
// report-service group → reads it, updates aggregates
// ALL 4 run simultaneously, each in their own time

// HOW CONSUMER GROUPS WORK:
// Topic: banking.transaction.events (6 partitions)
// Consumer group: notification-service-group
//   Instance 1 reads partitions 0, 1
//   Instance 2 reads partitions 2, 3
//   Instance 3 reads partitions 4, 5

// Consumer group: audit-service-group (completely independent)
//   Instance 1 reads ALL 6 partitions from its own offset
//   audit-service has its OWN offset pointer — doesn't share with notification-service

// ORDERING: messages with the same key go to the same partition
// Key = transactionId → all events for one transaction in same partition → same order
eventProducer.publishEvent(
    "banking.transaction.events",
    txn.getAccountId().toString(),  // key = accountId
    event                            // all events for one account → same partition → ordered
);
```

### Exactly-Once Semantics

```java
// THE PROBLEM:
// Network error after Kafka accepts message but before producer gets acknowledgment
// Producer retries → Kafka stores DUPLICATE message → notification sent twice → annoying user

// SOLUTION: Idempotent producer
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
// Kafka assigns each message a sequence number
// If producer retries: Kafka sees same sequence number → deduplicates → only stores once

config.put(ProducerConfig.ACKS_CONFIG, "all");
// "all": leader broker waits for ALL in-sync replicas to confirm before returning ack
// vs "1": only leader confirms (faster but can lose message if leader dies before replication)
// vs "0": fire and forget (fastest but no guarantee)
// For banking: ALWAYS use "all" — no message loss acceptable

// CONSUMER: manual acknowledgment prevents message loss
@KafkaListener(topics = "banking.transaction.events")
public void handle(String event, Acknowledgment ack) {
    try {
        processEvent(event);
        ack.acknowledge(); // ONLY after successful processing
        // Commits offset — Kafka won't redeliver this message
    } catch (Exception e) {
        // Don't ack — Kafka redelivers
        // Up to 3 times (our retry config), then DLQ
    }
}
```

### Outbox Pattern — Why It's Critical

```java
// THE PROBLEM (without Outbox):
@Transactional
public Payment initiatePayment(...) {
    Payment payment = Payment.builder()...build();
    paymentRepository.save(payment);  // Step 1: saves to DB

    // SERVER CRASHES HERE

    eventProducer.publishEvent(...); // Step 2: NEVER HAPPENS
    // Payment is in DB but no event published
    // transaction-service never debits the account
    // Money appears to "hang" — neither debited nor refunded
}

// THE SOLUTION (Outbox Pattern):
@Transactional
public Payment initiatePayment(...) {
    Payment payment = Payment.builder()
            .outboxProcessed(false)  // FLAG: "needs to be published"
            .build();
    paymentRepository.save(payment);  // DB transaction COMMITS with outboxProcessed=false

    // If server crashes here: payment is in DB with outboxProcessed=false
    // Scheduler will find it and publish on restart

    return payment;
}

// Scheduler runs every 5 seconds:
@Scheduled(fixedDelay = 5000)
@Transactional
public void processOutbox() {
    List<Payment> unpublished = paymentRepository.findByOutboxProcessedFalse();
    for (Payment payment : unpublished) {
        eventProducer.publishEvent("banking.payment.events", payment.getId().toString(), ...);
        payment.setOutboxProcessed(true);
        paymentRepository.save(payment); // marks as published
    }
}
// GUARANTEE: every payment eventually gets published, even after crashes
```

---

## Design Patterns — Interview Deep Dive

### Singleton Pattern

```java
// Every @Service in Spring is a Singleton — one instance shared by all threads
@Service
public class AccountService {
    // ONE instance exists for the lifetime of the application
    // 200 Tomcat threads all use this SAME AccountService instance simultaneously
    // SAFE: because there are no mutable instance variables

    private final AccountRepository accountRepository; // final, set once
    // No: private Account currentAccount; — would be a threading disaster

    public Account getAccountById(UUID id) {
        // local variable 'account' — each thread has its own on its stack
        Account account = accountRepository.findById(id).orElseThrow(...);
        return account;
    }
}
// Spring manages the lifecycle:
// - Creates the singleton during application startup
// - Injects it wherever @Autowired/constructor injection requests it
// - Destroys it when application shuts down
```

### Builder Pattern

```java
// Every entity and DTO uses @Builder (Lombok generates it)
// PROBLEM: Account has 12 fields — constructor with 12 params is unreadable
// new Account(userId, "ACC123", AccountType.CHECKING, AccountStatus.ACTIVE,
//             BigDecimal.ZERO, BigDecimal.ZERO, "USD", "021000021",
//             null, null, LocalDateTime.now(), LocalDateTime.now())
// Which param is which? What's at position 5?

// SOLUTION: Builder pattern
Account account = Account.builder()
        .userId(userId)                           // named, readable
        .accountType(Account.AccountType.CHECKING) // clear intent
        .balance(BigDecimal.ZERO)
        .availableBalance(BigDecimal.ZERO)
        .currencyCode("USD")
        .status(Account.AccountStatus.ACTIVE)
        .build();
// Optional fields not set = null or default (@Builder.Default)
// Order doesn't matter
// Can't accidentally swap two UUID arguments

// Lombok generates the entire builder class:
// Account.AccountBuilder builder = Account.builder();
// builder.userId(userId); builder.accountType(...);
// Account result = builder.build(); // validates and creates
```

### Factory Method Pattern

```java
// shared/common-utils — ApiResponse.java
// Static factory methods replace constructors when:
// - The constructor has complex logic
// - The name should describe what it creates
// - You want to return the same instance (cache) — less relevant here

public class ApiResponse<T> {
    // Named factory methods:
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        // Same type, different configuration
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}
// Usage reveals intent:
// ApiResponse.success(account)  — clearly a success response
// ApiResponse.error("Not found", "NOT_FOUND")  — clearly an error
// vs: new ApiResponse<>(true, null, account, null, null)  — unclear
```

### Observer Pattern (Kafka)

```java
// CLASSIC OBSERVER:
// Subject (publisher) maintains list of observers
// When event occurs, notifies all observers
// Tight coupling: publisher must know all observers

// KAFKA OBSERVER (Event-Driven, Loose Coupling):
// Publisher (transaction-service) doesn't know observers exist
// Publishes event to topic
// Observers (notification, audit, fraud) subscribe independently
// Adding a new observer: just create a new consumer group — publisher unchanged

// transaction-service (PUBLISHER/SUBJECT):
eventProducer.publishEvent("banking.transaction.events", txnId, event);
// transaction-service has zero knowledge of:
// - notification-service (doesn't import it)
// - audit-service (doesn't import it)
// - fraud-detection-service (doesn't import it)
// This is DECOUPLING — services are independent deployable units

// notification-service (OBSERVER):
@KafkaListener(topics = "banking.transaction.events", groupId = "notification-service-group")
public void onTransactionEvent(String event, Acknowledgment ack) {
    // Receives notification automatically when transaction occurs
    // notification-service registers itself as observer via groupId
    createNotification(...);
    sendEmail(...);
    ack.acknowledge();
}
```

### Saga Pattern

```java
// PROBLEM: Transfer money between accounts in different services (different databases)
// Can't use ONE database transaction — different DBs!

// SAGA SOLUTION: series of local transactions with compensation

// Step 1: payment-service saves payment, publishes PAYMENT_INITIATED
Payment payment = paymentRepository.save(
    Payment.builder().status(PaymentStatus.INITIATED).outboxProcessed(false).build()
);
// If this fails → nothing to compensate, return error

// Step 2: transaction-service consumes PAYMENT_INITIATED
// Debits sender account, publishes DEBIT_COMPLETED
// If this fails → publish PAYMENT_FAILED → payment-service marks as FAILED

// Step 3: transaction-service credits receiver account
// Publishes TRANSFER_COMPLETED
// If this fails → COMPENSATE: publish DEBIT_REVERSAL → undo the debit

// Each step publishes success OR compensation event
// Compensation = undo of a previous successful step
// Eventually consistent — not immediately consistent like a single DB transaction
// Acceptable for banking transfers where slight delay is okay
```

### Strategy Pattern

```java
// services/payment-service — different behavior per payment rail
// Instead of if-else for each rail, each has its own strategy

public interface PaymentProcessor {
    void process(Payment payment);
    boolean supports(Payment.PaymentRail rail);
}

@Component
public class SwiftPaymentProcessor implements PaymentProcessor {
    @Override
    public void process(Payment payment) {
        payment.setSwiftMessage(buildSwiftMT103(payment));
        // Submit to SWIFT network...
    }
    @Override
    public boolean supports(Payment.PaymentRail rail) {
        return rail == Payment.PaymentRail.SWIFT;
    }
}

@Component
public class AchPaymentProcessor implements PaymentProcessor {
    @Override
    public void process(Payment payment) {
        // Format ACH NACHA file...
    }
    @Override
    public boolean supports(Payment.PaymentRail rail) {
        return rail == Payment.PaymentRail.ACH;
    }
}

// PaymentService selects strategy at runtime:
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final List<PaymentProcessor> processors; // Spring injects ALL implementations

    public void initiatePayment(Payment payment) {
        PaymentProcessor processor = processors.stream()
                .filter(p -> p.supports(payment.getPaymentRail()))
                .findFirst()
                .orElseThrow(() -> new BankingException("Unsupported payment rail", ...));
        processor.process(payment);
    }
}
// Adding FedwirePaymentProcessor requires ZERO changes to PaymentService
```

**Interview Questions:**

- Q: What is the difference between the Saga and traditional distributed transaction (2PC)?
  A: "In our banking project, we handle cross-service money transfers using Saga choreography. Traditional 2PC (Two-Phase Commit) coordinates a lock across all databases simultaneously — all databases lock, coordinator decides commit or abort. This works but is slow, blocks resources, and fails if the coordinator goes down. Saga instead uses a series of local transactions — each service completes its local transaction and publishes an event. If a step fails, compensation events undo previous steps. The result: no distributed locks, services remain available, but the system is eventually consistent rather than immediately consistent. For banking transfers, eventual consistency is acceptable — the user doesn't need to see the credit in <100ms."

- Q: Explain the Builder pattern and why you used it.
  A: "In our banking project, the Account entity has 12 fields — balance, userId, accountType, status, currency, overdraftLimit, routingNumber, and audit timestamps. Without Builder, creating an Account would be: `new Account(uuid, 'ACC123', CHECKING, ACTIVE, ZERO, ZERO, 'USD', ...)` — 12 positional arguments. It's impossible to tell from the call site what each argument means. With Builder: `Account.builder().accountType(CHECKING).balance(ZERO).build()` — each field is named, optional fields are omitted, order doesn't matter. Lombok's @Builder generates all the builder code from the annotation. The result: readable, maintainable code with no risk of swapping two UUID parameters."

- Q: How does the Observer pattern differ from Kafka pub/sub?
  A: "The classic Observer pattern is in-memory and tightly coupled — the Subject maintains a list of Observer objects and calls their update() method directly. If we added fraud detection to account service, we'd have to import and register it in account service. With Kafka, transaction-service publishes to a topic without knowing any consumers. Fraud-detection-service independently subscribes. Adding a new consumer requires zero changes to the publisher. Kafka also adds: persistence (replay events), guaranteed delivery (ack/retry), high throughput (millions/second), and cross-service/cross-language support. Our banking app uses Kafka as a distributed observer pattern — decoupled, scalable, and fault-tolerant."
