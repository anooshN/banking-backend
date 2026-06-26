# Multithreading in This Project

## Yes, We Use Multithreading — Everywhere

Multithreading is happening at multiple levels in this application. Some of it is explicit (code we wrote), most of it is implicit (frameworks doing it for us). This document covers every place threads are used and exactly how.

---

## What Is Multithreading?

**Simple explanation:** Your computer has multiple CPUs (cores). Multithreading lets you run multiple tasks at the same time on those cores instead of waiting for one to finish before starting the next.

Single-threaded (bad for servers):
```
Request 1: process → wait for DB → done    (500ms total)
Request 2:                               → starts only after request 1 finishes
```

Multi-threaded (what we use):
```
Request 1: process → waiting for DB...
Request 2:           process → waiting for DB...     (both run simultaneously)
Request 3:                     process → done
```

---

## Level 1 — Tomcat Thread Pool (Implicit, Most Important)

**Every HTTP request runs on its own thread.**

Spring Boot uses an embedded Tomcat server. Tomcat maintains a thread pool:

```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200        # maximum concurrent request-handling threads
      min-spare: 10   # always keep 10 threads warm (ready instantly)
    connection-timeout: 20000  # 20 seconds
    accept-count: 100  # queue up to 100 requests when all threads busy
```

**What happens:**
```
1000 simultaneous HTTP requests arrive at auth-service:
  - Threads 1-200: each handles one request simultaneously
  - Requests 201-300: wait in accept queue (up to 100)
  - Request 301+: rejected with "Connection refused"

Each of the 200 threads:
  - Runs your @Service, @Repository code
  - Blocks while waiting for PostgreSQL response
  - Blocks while waiting for Redis response
  - Returns thread to pool after response sent
```

**Why 200 threads?** Most requests spend most of their time waiting for I/O (database, Redis, Kafka). A thread waiting for PostgreSQL is not using CPU — so you can have many threads. If everything was CPU-intensive, you'd want threads = CPU cores.

---

## Level 2 — @Async (Explicit, notification-service)

**@Async moves a method to a background thread so the caller doesn't wait.**

### Email Sending in notification-service

```java
// notification-service — EmailService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async  // ← THIS makes it run on a different thread
    public void sendEmail(String to, String subject, String body) {
        // This method runs on a thread from the async executor pool
        // The Kafka consumer thread that called this returns IMMEDIATELY
        // and continues processing the next message

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@bankingapp.com");
            mailSender.send(message);  // blocks here ~2-3 seconds (SMTP)
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
```

**Thread flow without @Async:**
```
Kafka consumer thread:
  1. Receives transaction event
  2. Creates notification in MongoDB (fast)
  3. Sends email via SMTP ← BLOCKS HERE for 2-3 seconds
  4. Only now acknowledges Kafka message

Problem: While email is sending, this thread can't process other Kafka messages
With 3 consumer threads and 3 emails sending simultaneously = all threads blocked
New events pile up in Kafka = growing consumer lag
```

**Thread flow with @Async:**
```
Kafka consumer thread:
  1. Receives transaction event
  2. Creates notification in MongoDB (fast)
  3. Calls emailService.sendEmail() → immediately returns (non-blocking)
  4. Acknowledges Kafka message ← happens in ~50ms
  5. Picks up next Kafka message immediately

Background async thread (from async pool):
  3a. Actually sends the email (~2-3 seconds, independently)
```

**Configuring the async thread pool:**
```java
// notification-service — AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // always keep 5 threads ready
        executor.setMaxPoolSize(20);      // grow up to 20 under load
        executor.setQueueCapacity(100);   // queue up to 100 tasks if all 20 busy
        executor.setThreadNamePrefix("async-email-");  // thread name for debugging
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.CallerRunsPolicy()
            // If queue full: run on the calling thread instead of dropping
        );
        executor.initialize();
        return executor;
    }
}
```

**@Async with CompletableFuture return type:**
```java
@Async
public CompletableFuture<String> sendEmailAndGetMessageId(String to, String subject) {
    String messageId = doSendEmail(to, subject);
    return CompletableFuture.completedFuture(messageId);
}

// Caller can:
CompletableFuture<String> future = emailService.sendEmailAndGetMessageId(to, subject);
// Do other work...
String messageId = future.get();  // block here when you actually need the result
```

---

## Level 3 — CompletableFuture (Explicit, kafka-lib)

**CompletableFuture represents a result that will be available in the future — used for async callbacks.**

```java
// shared/kafka-lib — BankingEventProducer.java
public void publishEvent(String topic, String key, Object event) {

    // kafkaTemplate.send() is NON-BLOCKING:
    // - Serializes the message
    // - Adds to internal batch buffer (in producer's own thread)
    // - Returns a CompletableFuture immediately
    // - Actual network send happens on Kafka producer's I/O thread
    CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topic, key, event);

    // Register a callback — runs on Kafka producer's thread when Kafka responds
    future.whenComplete((result, ex) -> {
        if (ex == null) {
            // SUCCESS: Kafka broker confirmed it received the message
            log.info("Published to topic: {} partition: {} offset: {}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } else {
            // FAILURE: Kafka rejected or timed out
            log.error("FAILED to publish to topic: {} error: {}", topic, ex.getMessage());
            // In production: send to alerting system, trigger incident
        }
    });
    // publishEvent() returns HERE — does not wait for Kafka's response
    // The callback runs later on Kafka's thread
}
```

**Thread timeline:**
```
HTTP request thread:
  1. Calls accountService.updateBalance()
  2. accountService calls eventProducer.publishEvent()
  3. publishEvent() puts message in Kafka producer buffer (microseconds)
  4. publishEvent() returns
  5. HTTP response sent to client ← in ~50ms

Kafka producer I/O thread (background):
  3a. Batches messages (up to 5ms linger)
  3b. Sends batch to Kafka broker over network
  3c. Waits for Kafka broker acknowledgment
  3d. whenComplete callback runs: logs success/failure
```

**CompletableFuture chaining (used in report-service):**
```java
// Run steps in sequence, each on potentially different threads:
CompletableFuture.supplyAsync(() -> fetchTransactions(accountId))  // thread pool
    .thenApply(transactions -> generatePdf(transactions))          // continues on same thread
    .thenApplyAsync(pdf -> uploadToS3(pdf))                        // ForkJoinPool thread
    .whenComplete((s3Key, ex) -> {
        if (ex == null) log.info("Statement ready: {}", s3Key);
        else log.error("Statement failed: {}", ex.getMessage());
    });
// Caller thread returns immediately — all steps run asynchronously
```

---

## Level 4 — Kafka Consumer Threads (Implicit, all consuming services)

**Each Kafka consumer listener runs on its own thread.**

```java
// shared/kafka-lib — KafkaConsumerConfig.java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object>
        kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();

    // setConcurrency(3): run 3 consumer threads per @KafkaListener method
    // With 6 partitions: each thread handles 2 partitions
    factory.setConcurrency(3);

    return factory;
}
```

**What this means in notification-service:**
```
3 consumer threads for "banking.transaction.events":
  Thread 1: reads from partitions 0, 1
  Thread 2: reads from partitions 2, 3
  Thread 3: reads from partitions 4, 5

Each thread independently:
  - polls Kafka for new messages
  - processes them (create MongoDB notification, call @Async email)
  - acknowledges (commits offset)
  - polls again

All 3 threads run simultaneously — 3x the throughput of 1 thread
```

**Thread safety: is it safe?** Each thread works on its own Kafka partition and its own messages. They don't share mutable state. MongoDB and Kafka writes are thread-safe. No synchronization needed.

---

## Level 5 — @Scheduled Thread (Implicit, payment-service)

**Spring's @Scheduled methods run on a single background thread (by default).**

```java
// payment-service — PaymentService.java
@Scheduled(fixedDelay = 5000)  // runs every 5 seconds
@Transactional
public void processOutbox() {
    List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();
    for (Payment payment : unprocessed) {
        eventProducer.publishEvent(...);
        payment.setOutboxProcessed(true);
        paymentRepository.save(payment);
    }
}
```

**Default scheduler: single-threaded.**
```
Time 0s: processOutbox() starts
Time 5s: previous run finishes → new run starts (fixedDelay = after completion)
Time 10s: next run
```

**Problem: fixedDelay vs fixedRate:**
- `fixedDelay = 5000`: wait 5s AFTER the previous run finishes
- `fixedRate = 5000`: run every 5s regardless (can overlap if run takes >5s)

For our outbox: `fixedDelay` is correct. If processing 1000 backed-up payments takes 30s, we don't want another run starting while that's in progress.

**Configure the scheduler thread pool (if you have many @Scheduled methods):**
```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);  // 5 threads for scheduled tasks
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
```

---

## Level 6 — Spring WebFlux / Reactor (API Gateway)

**The API Gateway is fully reactive — non-blocking, event-loop based.**

```java
// api-gateway — AuthenticationFilter.java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        // This lambda runs on a Netty event loop thread
        // Event loop threads MUST NEVER BLOCK (no Thread.sleep, no blocking I/O)
        // Blocking = all other requests on that event loop thread wait

        String path = exchange.getRequest().getPath().toString();

        // Redis check — using reactive RedisTemplate (non-blocking)
        return reactiveRedisTemplate.hasKey("blacklist:" + token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) return unauthorized(exchange);
                    return chain.filter(mutatedExchange);
                });
        // flatMap: "when the Redis response arrives, do this next step"
        // The event loop thread is NOT blocked waiting for Redis
        // It handles other requests while waiting
    };
}
```

**Reactor's threading model:**
```
Netty event loop threads (one per CPU core):
  Each thread handles thousands of connections
  Never blocks — schedules callbacks when I/O completes

I/O thread pool (for blocking operations):
  If you MUST block (e.g., legacy code): Schedulers.boundedElastic()
  .subscribeOn(Schedulers.boundedElastic())
  // moves the blocking operation to a different thread pool
```

**Why the Gateway is reactive but services are not:**
The Gateway is a pure router — it handles thousands of concurrent connections passing through. Reactive I/O (Netty) is far more efficient here than a thread-per-request model.

The business services (auth, accounts, transactions) use blocking Spring MVC + Tomcat because:
1. They do blocking I/O (JDBC to PostgreSQL) — hard to make truly reactive
2. The complexity cost of reactive doesn't pay off here
3. With a 200-thread Tomcat pool, they handle sufficient concurrency

---

## Level 7 — Spring Batch Parallel Processing (report-service)

**Spring Batch can process records in parallel across multiple threads.**

```java
// report-service — StatementJobConfig.java

@Bean
public Step fetchTransactionsStep() {
    return new StepBuilder("fetchTransactions", jobRepository)
            .<Transaction, StatementLine>chunk(100, transactionManager)
            // chunk(100): read 100 transactions, process 100, write 100 — repeat
            .reader(transactionReader())
            .processor(transactionProcessor())
            .writer(statementWriter())
            // Partition the step across multiple threads:
            .taskExecutor(batchTaskExecutor())
            .throttleLimit(4)  // run 4 threads simultaneously
            .build();
}

@Bean
public TaskExecutor batchTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setThreadNamePrefix("batch-statement-");
    executor.initialize();
    return executor;
}
```

**How parallel chunk processing works:**
```
Statement for account with 10,000 transactions:

Without parallel (single thread):
  Thread: chunk1(0-99) → chunk2(100-199) → ... → chunk100(9900-9999)
  Time: 100 × 50ms = 5,000ms (5 seconds)

With parallel (4 threads):
  Thread 1: chunk1, chunk5, chunk9, ...
  Thread 2: chunk2, chunk6, chunk10, ...
  Thread 3: chunk3, chunk7, chunk11, ...
  Thread 4: chunk4, chunk8, chunk12, ...
  Time: ~1,250ms (4x faster)
```

**Thread safety in batch:** Each chunk is processed by one thread at a time. The reader must be thread-safe (use `SynchronizedItemStreamReader` wrapper). The writer must be thread-safe (our S3 writer writes to separate file segments per thread).

---

## Level 8 — AtomicLong in Metrics (thread-safe counters)

```java
// shared/common-utils — BankingMetrics.java
public class BankingMetrics {

    private final AtomicLong activeSessionsGauge;
    // AtomicLong: thread-safe long — can be incremented from multiple threads
    // without synchronization

    public BankingMetrics(MeterRegistry registry) {
        // Micrometer wraps the AtomicLong in a Gauge metric
        this.activeSessionsGauge = registry.gauge(
                "banking.active.sessions",
                new AtomicLong(0)
        );
    }

    public void setActiveSessions(long count) {
        if (activeSessionsGauge != null) {
            activeSessionsGauge.set(count);
            // .set() is atomic — safe to call from any thread
        }
    }
}
```

**Why AtomicLong and not a regular long?**
```java
// WRONG — race condition:
private long count = 0;
public void increment() {
    count++;  // NOT atomic: read → add 1 → write
              // Two threads can both read 5, both write 6 → missed increment
}

// CORRECT — atomic:
private AtomicLong count = new AtomicLong(0);
public void increment() {
    count.incrementAndGet();  // single atomic CPU instruction (CAS — Compare And Swap)
                               // guaranteed correct from any number of threads
}
```

---

## Level 9 — Redis Operations (thread-safe by design)

```java
// fraud-detection-service — FraudEvaluationService.java
String velocityKey = "fraud:velocity:" + userId;

// 1000 concurrent requests for the same userId:
Long count = redisTemplate.opsForValue().increment(velocityKey);
// Redis INCR is atomic on the server side
// All 1000 requests get sequential values: 1, 2, 3, ..., 1000
// No duplicates, no lost increments
```

**How Redis handles concurrency:** Redis is single-threaded internally (since v6, with threaded I/O for network). All commands are executed sequentially on the main thread. `INCR` is therefore always atomic — no Java-level synchronization needed.

---

## Thread Safety Summary

| Component | Threading Model | Thread Safety Mechanism |
|---|---|---|
| HTTP requests | Tomcat thread pool (200 threads) | Each request on separate thread |
| Email sending | @Async (dedicated thread pool) | Fire-and-forget via CompletableFuture |
| Kafka publish | CompletableFuture + Kafka I/O thread | Non-blocking callback |
| Kafka consume | 3 consumer threads per @KafkaListener | Each thread owns its partitions |
| Outbox scheduler | Single scheduled thread | fixedDelay prevents overlap |
| API Gateway | Netty event loops (non-blocking) | Reactor's Mono/Flux chaining |
| Batch processing | ThreadPoolTaskExecutor (4-8 threads) | Chunk-level isolation |
| Metrics counters | AtomicLong | CPU-level CAS operations |
| Redis operations | Jedis/Lettuce connection pool | Redis server is single-threaded |
| Database operations | Hikari connection pool (20 connections) | One connection per thread |
| JPA @Transactional | Thread-bound transaction | ThreadLocal<EntityManager> |

---

## ThreadLocal — How @Transactional Works Under the Hood

Spring's @Transactional uses ThreadLocal to bind the database connection to the current thread:

```java
// Simplified view of what Spring does internally:
public class TransactionSynchronizationManager {

    // Each thread has its OWN map of resources
    private static final ThreadLocal<Map<Object, Object>> resources =
            new ThreadLocal<>();

    // When @Transactional method starts:
    // 1. Get a connection from Hikari pool
    // 2. Store it in this thread's ThreadLocal map
    // 3. Any @Repository call on this thread uses the SAME connection (same transaction)

    // When @Transactional method ends:
    // 1. Commit or rollback
    // 2. Remove connection from ThreadLocal
    // 3. Return connection to Hikari pool
}
```

This is why:
- All repository calls within one @Transactional method are part of the same transaction
- The transaction is invisible to other threads (they have their own ThreadLocal entry)
- You cannot share a transaction between threads

---

## Common Thread Safety Pitfalls We Avoid

**1. Shared mutable state in @Service (singleton):**
```java
// BAD — instance variable shared by all threads:
@Service
public class BadAccountService {
    private Account currentAccount;  // 200 threads share this!

    public Account getAccount(UUID id) {
        currentAccount = accountRepository.findById(id).orElseThrow();
        return currentAccount;  // Thread A sets it, Thread B overwrites it → bugs
    }
}

// GOOD — local variable per method call:
@Service
public class GoodAccountService {
    public Account getAccount(UUID id) {
        Account account = accountRepository.findById(id).orElseThrow();
        // account is local to this method call — not shared between threads
        return account;
    }
}
```

**Our services have no mutable instance variables.** All fields are `final` (injected once in constructor) or `private final` Spring-managed beans — all thread-safe.

**2. SimpleDateFormat (not thread-safe — we don't use it):**
```java
// BAD — SimpleDateFormat is NOT thread-safe
private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

// GOOD — DateTimeFormatter IS thread-safe
private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
// We use Java 8+ time API throughout (LocalDateTime, etc.)
```
