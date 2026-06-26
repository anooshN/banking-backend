# Module 7 — Multithreading & Concurrency

---

## 1. Thread Basics and Tomcat Thread Pool

**Situation:**
The banking app serves thousands of concurrent users. Each HTTP request must be handled simultaneously — a single thread would process one request at a time, making the app unusably slow.

**Task:**
Understand how Tomcat manages threads and how our code runs concurrently.

**Action:**

```java
// Every HTTP request runs on its own Tomcat thread.
// Configuration in application.yml:
server:
  tomcat:
    threads:
      max: 200        # 200 simultaneous requests handled
      min-spare: 10   # always 10 threads ready (no startup delay)

// What happens with 500 simultaneous requests:
// Threads 1-200: each handles one request simultaneously
// Requests 201-300: wait in queue (accept-count: 100)
// Request 301: "Connection refused" — too many concurrent connections

// Each of the 200 threads runs YOUR code:
// Thread 47: executing AccountService.getAccountById()
// Thread 48: executing TransactionService.debit() — simultaneously
// Thread 49: executing AuthService.login() — simultaneously
```

```java
// Thread-safety of @Service beans:
// @Service creates SINGLETON — one instance shared by ALL threads
@Service
@RequiredArgsConstructor
public class AccountService {
    // SAFE: final dependencies injected once at startup — never changed
    private final AccountRepository accountRepository;
    private final BankingEventProducer eventProducer;

    // SAFE: all data is in LOCAL VARIABLES (on each thread's stack)
    public Account getAccountById(UUID accountId) {
        // 'account' variable — each thread has its OWN copy on its stack
        Account account = accountRepository.findById(accountId).orElseThrow(...);
        return account;
        // Thread 47 and Thread 48 both execute this simultaneously
        // They each have their own 'account' variable — no conflict
    }

    // DANGEROUS — would NOT be safe:
    // private Account currentAccount; // shared by all threads!
    // Thread 47 sets it, Thread 48 overwrites it before Thread 47 reads it
}
```

---

## 2. @Async — Background Thread Execution

**Situation:**
In the notification service, sending emails via SMTP takes 2-3 seconds. If the Kafka consumer thread waits for the email to send, it can't process the next notification — growing consumer lag.

**Task:**
Move email sending to a background thread so the Kafka consumer thread is freed immediately.

**Action:**

```java
// services/notification-service — EmailService.java
@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Async  // ← moves this method to a background thread pool
    public void sendEmail(String to, String subject, String body) {
        // Runs on a thread from the async executor pool
        // The calling thread returns IMMEDIATELY
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);  // blocks for 2-3 seconds — on background thread
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            // Exception logged but not propagated (caller already returned)
        }
    }
}

// WITHOUT @Async — timeline:
// Kafka consumer thread:
// t=0ms:   receives transaction event
// t=5ms:   saves notification to MongoDB
// t=5ms:   calls emailService.sendEmail() — BLOCKS HERE
// t=2500ms: email sent
// t=2500ms: ack.acknowledge() called
// t=2500ms: picks up next event
// → Can only process ~400 events/minute per thread

// WITH @Async — timeline:
// Kafka consumer thread:
// t=0ms:   receives transaction event
// t=5ms:   saves notification to MongoDB
// t=5ms:   calls emailService.sendEmail() — RETURNS IMMEDIATELY
// t=6ms:   ack.acknowledge() called
// t=6ms:   picks up next event
// → Can process ~10,000 events/minute per thread

// Background async thread (from pool):
// t=5ms:   picks up email task from queue
// t=2500ms: email sent
// Completely independent from Kafka consumer thread

// Configure the async thread pool:
@Configuration
@EnableAsync  // REQUIRED on the Application class or a @Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // always 5 threads ready
        executor.setMaxPoolSize(20);      // grow to 20 under load
        executor.setQueueCapacity(100);   // queue up to 100 pending tasks
        executor.setThreadNamePrefix("async-email-");  // visible in thread dumps
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
                // Queue full: run on calling thread — prevents task loss
        );
        executor.initialize();
        return executor;
    }
}
```

---

## 3. CompletableFuture — Async Results

**Situation:**
Publishing to Kafka should not block the HTTP request thread. But we want to log success/failure when Kafka responds. CompletableFuture lets us register a callback that runs when the async operation completes.

**Task:**
Use CompletableFuture for non-blocking async operations with callbacks.

**Action:**

```java
// shared/kafka-lib — BankingEventProducer.java
public void publishEvent(String topic, String key, Object event) {

    // send() is non-blocking — puts message in producer's buffer and returns
    CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topic, key, event);

    // Register callback — runs on Kafka's I/O thread when Kafka broker responds
    future.whenComplete((result, ex) -> {
        if (ex == null) {
            // SUCCESS
            log.info("Published: topic={} partition={} offset={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } else {
            // FAILURE — Kafka rejected or timed out
            log.error("FAILED publish: topic={} key={} error={}",
                    topic, key, ex.getMessage());
        }
    });
    // publishEvent() returns HERE — does NOT wait for Kafka response
}

// CompletableFuture CHAINING — sequential async steps:
CompletableFuture<String> statementKey =
    CompletableFuture
        .supplyAsync(() -> fetchTransactions(accountId))    // step 1: on ForkJoin thread
        .thenApply(transactions -> generatePdf(transactions)) // step 2: same thread
        .thenApplyAsync(pdf -> uploadToS3(pdf))               // step 3: different pool thread
        .exceptionally(ex -> {                                 // error handler
            log.error("Statement generation failed: {}", ex.getMessage());
            return null; // return null on failure
        });
// The calling thread is free — all steps run asynchronously

// Block when you actually need the result:
String s3Key = statementKey.get(30, TimeUnit.SECONDS);
// Blocks for max 30 seconds — throws TimeoutException if not done

// CompletableFuture combining — run in parallel:
CompletableFuture<BigDecimal> balanceFuture =
        CompletableFuture.supplyAsync(() -> accountServiceClient.getBalance(accountId));
CompletableFuture<FraudScore> fraudFuture =
        CompletableFuture.supplyAsync(() -> fraudService.evaluate(transactionId, ...));

// Wait for BOTH to complete:
CompletableFuture.allOf(balanceFuture, fraudFuture).join();
BigDecimal balance = balanceFuture.get();
FraudScore score = fraudFuture.get();
// Both ran simultaneously — total time = max(balance time, fraud time)
// Not: balance time + fraud time (which serial would be)
```

---

## 4. ThreadLocal — Thread-Scoped Storage

**Situation:**
The CorrelationId must be available throughout the entire request processing — in the filter, in the service, in the AuditAspect — without passing it as a parameter everywhere.

**Task:**
Use MDC (which uses ThreadLocal) to store the correlationId once and access it anywhere in the same thread.

**Action:**

```java
// shared/common-utils — CorrelationIdFilter.java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();  // generate if not provided
        }

        // MDC.put() stores value in ThreadLocal under the hood:
        MDC.put(MDC_CORRELATION_ID, correlationId);
        // Now ANY log statement on this thread automatically includes correlationId:
        // [2024-01-15 10:23:45] [correlationId=abc-123] INFO AccountService - Account fetched

        response.setHeader("X-Correlation-ID", correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: remove from ThreadLocal when request is done
            // Without this: ThreadLocal leaks — Tomcat reuses threads
            // Thread 47 finishes request, gets reused for next request
            // Next request would see the previous request's correlationId
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}

// In AuditAspect — reading correlationId from same thread:
@Around("@annotation(auditable)")
public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
    String correlationId = MDC.get("correlationId");
    // Gets the value set by CorrelationIdFilter — on the SAME thread
    // Thread 47's MDC has correlationId=abc-123
    // Thread 48's MDC has correlationId=xyz-789
    // They don't interfere — ThreadLocal is per-thread

    // Include in audit event:
    auditPayload.put("correlationId", correlationId);
    // ...
}

// HOW ThreadLocal WORKS (simplified):
// Each Thread has an internal Map<ThreadLocal<?>, Object>
// MDC.put("key", "value") → Thread.currentThread().threadLocalMap.put(thisThreadLocal, value)
// MDC.get("key") → Thread.currentThread().threadLocalMap.get(thisThreadLocal)
// Thread 47's map and Thread 48's map are separate — perfect isolation
```

---

## 5. Atomic Classes — Thread-Safe Counters

**Situation:**
Metrics counters (active sessions, transaction count) are incremented by 200 simultaneous Tomcat threads. Regular `long++` is not atomic — two threads can both read 5, both write 6, missing one increment.

**Task:**
Use AtomicLong for counters that are incremented from multiple threads.

**Action:**

```java
// shared/common-utils — BankingMetrics.java
@Component
public class BankingMetrics {

    private final AtomicLong activeSessionsGauge;

    public BankingMetrics(MeterRegistry registry) {
        // Micrometer wraps AtomicLong in a Gauge metric (always reflects current value)
        this.activeSessionsGauge = registry.gauge(
                "banking.active.sessions",
                new AtomicLong(0)
        );
    }

    // Called from many threads simultaneously:
    public void setActiveSessions(long count) {
        if (activeSessionsGauge != null) {
            activeSessionsGauge.set(count);
            // .set() is atomic — thread-safe, no synchronization needed
        }
    }
}

// WHY AtomicLong and NOT synchronized long:
// Option 1: synchronized block — SLOW (only one thread at a time)
private long counter = 0;
public synchronized void increment() {
    counter++;  // works but blocks all other threads
}

// Option 2: AtomicLong — FAST (uses CPU-level Compare-and-Swap)
private AtomicLong counter = new AtomicLong(0);
public void increment() {
    counter.incrementAndGet();
    // CAS (Compare And Swap):
    // 1. Read current value: 5
    // 2. Calculate new value: 6
    // 3. Compare-and-swap: "set to 6 ONLY IF current is still 5"
    // 4. If another thread changed it to 6 between step 1 and 3: retry
    // Result: always correct, never blocks threads
}

// AtomicLong operations:
counter.get()                    // read
counter.set(100)                 // write
counter.incrementAndGet()        // ++counter (return new value)
counter.getAndIncrement()        // counter++ (return old value)
counter.addAndGet(5)             // counter += 5
counter.compareAndSet(5, 10)     // if(counter==5) counter=10; returns true/false
```

---

## 6. Kafka Consumer Threads and Concurrent Processing

**Situation:**
A single Kafka consumer thread can process ~3,000 messages/minute. Our banking app generates 50,000 notifications/minute at peak. We need parallel consumption.

**Task:**
Configure concurrent Kafka consumers to process messages in parallel.

**Action:**

```java
// shared/kafka-lib — KafkaConsumerConfig.java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object>
        kafkaListenerContainerFactory() {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(consumerFactory());

    // 3 consumer threads per @KafkaListener method:
    factory.setConcurrency(3);
    // Our topic has 6 partitions:
    // Thread 1: reads partitions 0, 1
    // Thread 2: reads partitions 2, 3
    // Thread 3: reads partitions 4, 5
    // 3x throughput vs single consumer

    // Each thread independently:
    // - polls Kafka for messages (batch)
    // - processes them
    // - acknowledges
    // - polls again

    // Manual acknowledgment — each thread acks its own messages:
    factory.getContainerProperties()
           .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    return factory;
}

// @KafkaListener — runs on 3 threads simultaneously:
@KafkaListener(topics = "banking.transaction.events")
public void handleEvent(String event, Acknowledgment ack) {
    // This method is called concurrently by 3 threads
    // Thread-safety: no shared mutable state in this method
    // Each call has its own local variables
    try {
        processEvent(event);  // MongoDB write — thread-safe (each has own connection)
        emailService.sendEmail(...);  // @Async — moves to another pool
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Failed: {}", e.getMessage());
        // Don't ack — Kafka redelivers
    }
}
```

---

## 7. @Scheduled and Its Thread

**Situation:**
The payment outbox processor runs every 5 seconds and publishes unprocessed payments to Kafka. It must not run concurrently with itself.

**Task:**
Use @Scheduled with fixedDelay (not fixedRate) to ensure sequential, non-overlapping execution.

**Action:**

```java
// services/payment-service — PaymentService.java
@Scheduled(fixedDelay = 5000)
// fixedDelay = 5000ms AFTER the previous run COMPLETES
// If run takes 30 seconds (1000 payments): next run starts 35s after start of first
// Guarantees: two runs never overlap
@Transactional
public void processOutbox() {
    List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();
    for (Payment payment : unprocessed) {
        eventProducer.publishEvent(...);
        payment.setOutboxProcessed(true);
        paymentRepository.save(payment);
    }
}

// fixedDelay vs fixedRate:
// @Scheduled(fixedDelay = 5000):
//   Run 1: 0s → 30s (30 second run)
//   Run 2: 35s → 35.5s
//   Run 3: 40.5s → 41s
//   Runs never overlap

// @Scheduled(fixedRate = 5000):
//   Run 1: 0s → 30s
//   Run 2: 5s → 35s (started while run 1 still running!)
//   DANGER: two runs processing same payments simultaneously
//   Could publish same payment to Kafka twice (double charging!)
// For outbox: ALWAYS use fixedDelay

// Monthly statement scheduler:
@Scheduled(cron = "0 0 1 1 * *")
// second minute hour day month weekday
// 0     0      1    1   *     *  = 01:00 on the 1st of every month
public void generateMonthlyStatements() {
    log.info("Starting monthly statement batch for all accounts");
    // kicks off Spring Batch jobs
}
```

**Interview Questions:**

- Q: How does @Async work internally?
  A: "In our notification service, we faced a situation where email sending (2-3 seconds) was blocking Kafka consumer threads. @Async works through Spring AOP — Spring creates a proxy around EmailService. When `sendEmail()` is called, the proxy intercepts it, wraps the actual method call in a Runnable, submits it to the configured ThreadPoolTaskExecutor, and returns to the caller immediately. The actual email sending runs on a background thread from the pool. The result: our Kafka consumer processes 10,000 notifications/minute instead of 400."

- Q: What is a race condition? How do you prevent it?
  A: "In our fraud detection service, we track transaction count per user in Redis using INCR. A race condition would occur if we read the count, add 1, and write it back — two threads could both read 5, both calculate 6, and both write 6 instead of 7. Redis's INCR command is atomic on the server side — it's a single operation that can't be interrupted. In our Java code, for counters, we use AtomicLong with compareAndSet — CPU-level atomic operations. For our service classes, we avoid race conditions by having no mutable shared state — all data is in local variables or database/Redis (which handle concurrency themselves)."

- Q: What is the difference between synchronized and AtomicLong?
  A: "Both provide thread safety for counters. `synchronized` uses a monitor lock — only one thread at a time enters the synchronized block, others wait (mutex). This is safe but creates a bottleneck — 200 threads all waiting to increment a counter. AtomicLong uses CAS (Compare-And-Swap) — a CPU instruction that atomically reads, computes, and writes without locking. No thread waits — if two threads try simultaneously, one succeeds and the other retries. In our BankingMetrics, we use AtomicLong because it has far higher throughput than synchronized under high concurrency."
