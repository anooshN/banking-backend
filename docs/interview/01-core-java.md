# Module 01 — Core Java Fundamentals

---

## 1. Variables & Data Types

### Concept
Java has two kinds of variables:
- **Primitive** — stores the value directly (`int`, `long`, `double`, `boolean`, `char`, `byte`, `short`, `float`)
- **Reference** — stores the memory address of an object (`String`, `UUID`, `BigDecimal`, any class)

---

### STAR Answer — Why We Used BigDecimal Instead of double for Money

**Situation:**
In our banking application, every account balance, transaction amount, and payment value involved money. We needed to store and calculate these values precisely.

**Task:**
Choose the right data type for monetary values. The wrong choice could cause calculation errors — for example, charging a customer the wrong amount.

**Action:**
We used `BigDecimal` throughout the entire codebase instead of `double` or `float`.

```java
// In Account entity:
@Column(nullable = false, precision = 19, scale = 4)
private BigDecimal balance;

// In TransactionService — debit logic:
BigDecimal currentBalance = accountServiceClient.getBalance(accountId);

if (currentBalance.compareTo(amount) < 0) {
    throw new InsufficientFundsException(accountId.toString());
}

BigDecimal balanceAfter = currentBalance.subtract(amount);

// In LoanService — EMI calculation:
BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
BigDecimal emi = principal.multiply(monthlyRate).multiply(power)
                          .divide(denominator, 2, RoundingMode.HALF_UP);
```

**Result:**
Zero rounding errors in financial calculations. If we had used `double`:
```java
double a = 0.1 + 0.2;
System.out.println(a); // prints 0.30000000000000004 — WRONG!

// This error compounds across thousands of transactions:
// 1000 transactions × 0.000000000000001 error = small but real discrepancy
// In banking, even 1 paisa wrong = compliance violation
```

---

### STAR Answer — Why We Used UUID Instead of int for Primary Keys

**Situation:**
Our banking app has 14 microservices, each with its own database. Multiple services generate records simultaneously.

**Task:**
Choose a primary key type that is unique across ALL services and ALL databases — without any coordination.

**Action:**
We used `UUID` as the primary key for every entity:

```java
// In Account entity:
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
// PostgreSQL generates: uuid_generate_v4() → "550e8400-e29b-41d4-a716-446655440000"

// In controllers — URL parameters:
@GetMapping("/{accountId}")
public ResponseEntity<?> getAccount(@PathVariable UUID accountId) {
    // Spring automatically converts "550e8400-..." string → UUID object
}

// In service code:
String correlationId = UUID.randomUUID().toString();
// Every request gets a unique tracking ID for tracing across services
```

**Result:**
- **Security:** With `int` IDs (1, 2, 3...), an attacker guesses `id=1001` to access another user's account. UUIDs are unguessable.
- **No coordination:** Services generate UUIDs independently — no central ID generator needed.
- **Cross-service:** A transaction can reference an `accountId` UUID without knowing which database or service owns it.

---

### STAR Answer — Variables and Scope in Auth Service

**Situation:**
In our auth-service, the account lockout logic requires tracking failed login attempts and comparing timestamps.

**Task:**
Use the right variable types and scopes to implement lockout safely.

**Action:**
```java
// Class-level constant (static final) — shared, never changes
private static final int MAX_LOGIN_ATTEMPTS = 5;
private static final String BLACKLIST_PREFIX = "blacklist:";
private static final String REFRESH_PREFIX   = "refresh:";

// Instance-level final fields — injected once, immutable
private final UserRepository userRepository;
private final PasswordEncoder passwordEncoder;
private final JwtTokenProvider jwtTokenProvider;

// Method-level local variables — exist only during this method call
public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
                              .orElseThrow(...);          // local variable

    LocalDateTime lockUntil = user.getLockedUntil();      // local variable

    if (lockUntil != null && LocalDateTime.now().isBefore(lockUntil)) {
        throw new BankingException("Account locked", ...);
    }
    // lockUntil is garbage collected when method ends
}
```

**Result:**
- `static final` constants → one copy shared, readable across the class
- `final` instance fields → thread-safe (cannot be changed after construction)
- Local variables → live only as long as needed, no memory leaks

---

### STAR Answer — Strings in the Banking App

**Situation:**
Strings are used heavily — JWT tokens, account numbers, correlation IDs, SWIFT messages, card number masking, email addresses.

**Task:**
Use String correctly — immutability, formatting, and manipulation — without performance issues.

**Action:**
```java
// 1. String.format() — SWIFT MT103 message builder in PaymentService:
private String buildSwiftMT103(Payment payment) {
    return String.format(":20:%s:32A:%s%s%.2f:59:%s",
            payment.getPaymentReference(),
            LocalDate.now(),
            payment.getCurrencyCode(),
            payment.getAmount(),
            payment.getReceiverName());
    // Output: ":20:SWI123:32A:2024-01-15USD500.00:59:Jane Smith"
}

// 2. String.substring() — card number masking in CardService:
private String maskCardNumber(String cardNumber) {
    // "4111111111111111" → "**** **** **** 1111"
    return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
}

// 3. String.startsWith() — JWT extraction in JwtAuthenticationFilter:
String bearerToken = request.getHeader("Authorization");
if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
    return bearerToken.substring(7); // remove "Bearer " prefix → get raw token
}

// 4. String concatenation with + in logs (OK for low-frequency):
log.info("Account created: " + account.getAccountNumber() + " for user: " + userId);

// 5. StringBuilder for building multiple pieces (efficient):
StringBuilder swiftMsg = new StringBuilder();
swiftMsg.append(":20:").append(reference)
        .append(":32A:").append(currency).append(amount)
        .append(":59:").append(receiverName);
// StringBuilder is mutable — avoids creating intermediate String objects

// 6. String immutability matters for thread safety:
private final String JWT_SECRET = "my-secret-key"; // safely shared across threads
// Strings are immutable — no thread can change the value → naturally thread-safe
```

**Result:**
- `String.format()` makes SWIFT message building readable and maintainable
- `substring()` for card masking protects PCI-DSS compliance (never store full card number)
- `StringBuilder` in high-frequency loops avoids creating hundreds of temporary String objects
- Immutability means JWT secret shared across 200 Tomcat threads with zero synchronization

---

## 2. Operators

### STAR Answer — Operators in Our Banking Project

**Situation:**
The banking app needed to perform balance comparisons, calculate fraud scores, check conditions for account locking, and compute loan EMIs.

**Task:**
Use Java operators correctly for financial calculations and business logic.

**Action:**

```java
// ── ARITHMETIC OPERATORS ─────────────────────────────────────────────────────
// In TransactionService — balance update:
// NOT: balance = balance + amount  (double arithmetic — rounding errors)
// YES: BigDecimal arithmetic — exact
BigDecimal balanceAfter = currentBalance.subtract(amount);       // -
BigDecimal balanceAfter = currentBalance.add(amount);            // +
BigDecimal monthlyRate  = annualRate.divide(
        BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);     // ÷

// In FraudEvaluationService — score accumulation:
double score = 0.0;
score += 0.30;  // += compound assignment operator
score += 0.50;
score = Math.min(score, 1.0); // cap at 1.0

// In CardService — last 4 digits:
String last4 = String.format("%04d", (int)(Math.random() * 10000));
// % = modulo (not used directly here but Math.random() * 10000 → 0-9999)

// ── COMPARISON OPERATORS ─────────────────────────────────────────────────────
// BigDecimal: NEVER use == or != (compares object references, not values)
// 10.00 == 10.0 → FALSE (different scale, different object)
// ALWAYS use compareTo():
if (currentBalance.compareTo(amount) < 0) {      // balance < amount → insufficient funds
    throw new InsufficientFundsException(accountId.toString());
}
if (currentBalance.compareTo(BigDecimal.ZERO) == 0) {  // balance == 0
    log.warn("Account has zero balance");
}

// For primitives == is fine:
if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {  // >= operator
    user.setStatus(User.UserStatus.LOCKED);
}

// ── LOGICAL OPERATORS ────────────────────────────────────────────────────────
// In JwtAuthenticationFilter:
if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
    // && = AND: both conditions must be true
    // Short-circuit: if hasText() is false → startsWith() is NOT evaluated
}

// In AuthService — account lockout check:
if (user.getLockedUntil() != null
        && LocalDateTime.now().isBefore(user.getLockedUntil())) {
    // != null check FIRST (short-circuit prevents NullPointerException)
    throw new BankingException("Account locked", ...);
}

// In ProtectedRoute (frontend TypeScript — same concept):
if (!isAuthenticated) { redirect to login }

// ── TERNARY OPERATOR ─────────────────────────────────────────────────────────
// In FraudEvaluationService — risk level:
FraudScore.FraudRisk risk = score < 0.3  ? FraudScore.FraudRisk.LOW
                           : score < 0.5  ? FraudScore.FraudRisk.MEDIUM
                           : score < 0.8  ? FraudScore.FraudRisk.HIGH
                           : FraudScore.FraudRisk.CRITICAL;
// Nested ternary: clean for simple multi-value assignment

// In Header.tsx (frontend):
{unreadCount > 9 ? '9+' : unreadCount}
// Display '9+' if more than 9, else show actual count

// ── INSTANCEOF OPERATOR ──────────────────────────────────────────────────────
// In GlobalExceptionHandler — check exception type before casting:
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handle(Exception ex) {
    if (ex instanceof BankingException be) {       // Java 16 pattern matching
        return ResponseEntity.status(be.getHttpStatus())
                .body(ApiResponse.error(be.getMessage(), be.getErrorCode()));
    }
    return ResponseEntity.status(500)
            .body(ApiResponse.error("Unexpected error", "INTERNAL_SERVER_ERROR"));
}
```

**Result:**
- `compareTo()` for BigDecimal prevents the most common banking bug: wrong money comparison
- Short-circuit `&&` prevents NullPointerException in null checks
- Ternary makes fraud scoring readable in one expression instead of 8 lines of if-else
- Pattern matching `instanceof` eliminates manual casting — cleaner and safer

---

## 3. Control Statements

### STAR Answer — if/else: Account Lockout Logic

**Situation:**
Our banking app needed to protect user accounts from brute-force password attacks (automated bots trying thousands of passwords).

**Task:**
Implement an account lockout system that blocks an account after 5 failed login attempts for 30 minutes.

**Action:**
```java
// In AuthService.java — login method:
public AuthResponse login(LoginRequest request) {

    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() ->
                new BankingException("Invalid credentials", "INVALID_CREDENTIALS",
                        HttpStatus.UNAUTHORIZED));

    // if: is the account currently locked?
    if (user.getStatus() == User.UserStatus.LOCKED) {

        // nested if: has the lockout period expired?
        if (user.getLockedUntil() != null
                && LocalDateTime.now().isBefore(user.getLockedUntil())) {
            throw new BankingException(
                "Account locked. Try again later.",
                "ACCOUNT_LOCKED",
                HttpStatus.FORBIDDEN
            );
        } else {
            // lockout expired — automatically unlock
            user.setStatus(User.UserStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
        }
    }

    // if-else: password check
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        handleFailedLogin(user);  // increment attempt counter
        throw new BankingException("Invalid credentials", "INVALID_CREDENTIALS",
                HttpStatus.UNAUTHORIZED);
    }

    // success path: reset counter
    user.setFailedLoginAttempts(0);
    userRepository.save(user);

    return generateTokens(user);
}

private void handleFailedLogin(User user) {
    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

    if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
        user.setStatus(User.UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        log.warn("Account LOCKED: {} after {} failed attempts",
                user.getEmail(), MAX_LOGIN_ATTEMPTS);
    }
    userRepository.save(user);
}
```

**Result:**
After 5 wrong passwords, the account is locked for 30 minutes. A bot trying 1000 passwords per second gets blocked after 5 attempts — preventing brute-force account takeover.

---

### STAR Answer — Switch Expression: Payment Rail Routing

**Situation:**
Our payment-service handles 5 different payment rails (SWIFT, FEDWIRE, CHIPS, ACH, INTERNAL). Each rail has different processing logic, fee structures, and message formats.

**Task:**
Route each payment to the correct processor based on the payment rail — cleanly and without fall-through bugs.

**Action:**
```java
// In PaymentService.java — routing logic:
private void processPaymentByRail(Payment payment) {
    switch (payment.getPaymentRail()) {
        case SWIFT -> {
            // Build SWIFT MT103 message
            String mt103 = buildSwiftMT103(payment);
            payment.setSwiftMessage(mt103);
            swiftGateway.submit(mt103);
            log.info("SWIFT payment submitted: {}", payment.getPaymentReference());
        }
        case FEDWIRE -> {
            fedwireClient.initiate(payment.getAmount(),
                    payment.getReceiverAccountNumber(),
                    payment.getReceiverBankCode());
        }
        case ACH -> {
            achProcessor.scheduleNextDay(payment);
            // ACH takes 1-3 business days
        }
        case CHIPS -> {
            chipsNetwork.submitSameDay(payment);
        }
        case INTERNAL -> {
            // No external network needed — just update balances
            internalTransfer(payment);
        }
    }
}

// Switch expression returning a value — for fee calculation:
BigDecimal feePercent = switch (payment.getPaymentRail()) {
    case SWIFT    -> new BigDecimal("0.002");   // 0.2% for international
    case FEDWIRE  -> new BigDecimal("0.001");   // 0.1% for domestic wire
    case ACH      -> new BigDecimal("0.0005");  // 0.05% for ACH
    case CHIPS    -> new BigDecimal("0.001");
    case INTERNAL -> BigDecimal.ZERO;           // free internal transfers
};
BigDecimal fee = payment.getAmount().multiply(feePercent);
```

**Result:**
Switch expressions (Java 14+) prevent the classic fall-through bug of the old switch statement. No `break` needed. The compiler also warns if a case is missing — so if we add a new payment rail enum value, the compiler immediately tells us we forgot to handle it.

---

### STAR Answer — For Loop: Outbox Pattern Processing

**Situation:**
The payment-service uses the Outbox Pattern — payments are saved to the database with `outbox_processed = false`, and a background job must publish them to Kafka.

**Task:**
Iterate over all unprocessed payments and publish each one to Kafka safely.

**Action:**
```java
// In PaymentService.java — @Scheduled outbox processor:
@Scheduled(fixedDelay = 5000)
@Transactional
public void processOutbox() {
    // Find all unprocessed payments
    List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();

    // Enhanced for loop (for-each) — cleaner than index-based for
    for (Payment payment : unprocessed) {
        try {
            // Publish to Kafka
            eventProducer.publishEvent(
                BankingConstants.TOPIC_PAYMENT_EVENTS,
                payment.getId().toString(),
                "PAYMENT_INITIATED:" + payment.getPaymentReference()
                        + ":" + payment.getPaymentRail()
            );
            // Mark as processed
            payment.setOutboxProcessed(true);
            paymentRepository.save(payment);

            log.info("Outbox processed: {}", payment.getPaymentReference());

        } catch (Exception e) {
            // Don't let one failure stop the others
            // This payment will be retried on next scheduler run
            log.error("Failed to process outbox payment: {} error: {}",
                    payment.getId(), e.getMessage());
        }
    }
}

// In KafkaConsumerConfig — retry logic uses traditional for loop:
for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
        processEvent(event);
        break; // success → exit loop
    } catch (Exception e) {
        if (attempt == MAX_RETRIES) {
            sendToDlq(event, e); // final attempt failed → DLQ
        }
        Thread.sleep(1000L * attempt); // exponential backoff: 1s, 2s, 3s
    }
}
```

**Result:**
The for-each loop processes each payment independently — one failure doesn't stop the others. With the traditional for loop, the retry logic with exponential backoff gives Kafka time to recover from transient issues before sending the message to the DLQ.

---

## 4. Arrays

### STAR Answer — Arrays in Fraud Detection

**Situation:**
When the fraud detection service evaluates a transaction, it can flag it for multiple reasons simultaneously (high value AND new IP address AND high velocity). We need to return all reasons together.

**Task:**
Store and return multiple fraud detection reasons for a single transaction evaluation.

**Action:**
```java
// In FraudScore model — array field:
@Data
@Builder
public class FraudScore {
    private String transactionId;
    private double score;
    private FraudRisk riskLevel;
    private String[] reasons;      // array of reason codes
    private LocalDateTime evaluatedAt;
}

// In FraudEvaluationService — building the reasons array:
public FraudScore evaluate(String transactionId, String userId,
                            BigDecimal amount, String ip) {
    double score = 0.0;
    List<String> reasons = new ArrayList<>();  // List first — dynamic size

    if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
        score += 0.3;
        reasons.add("HIGH_VALUE_TRANSACTION");
    }

    Long txnCount = redisTemplate.opsForValue().increment("fraud:velocity:" + userId);
    if (txnCount != null && txnCount > MAX_TRANSACTIONS_PER_HOUR) {
        score += 0.5;
        reasons.add("HIGH_VELOCITY");
    }

    Boolean isNewIp = redisTemplate.opsForValue()
            .setIfAbsent("fraud:ip:" + userId + ":" + ip, "1", 30, TimeUnit.DAYS);
    if (Boolean.TRUE.equals(isNewIp)) {
        score += 0.1;
        reasons.add("NEW_IP_ADDRESS");
    }

    // Convert List → array for the final result:
    return FraudScore.builder()
            .transactionId(transactionId)
            .score(Math.min(score, 1.0))
            .riskLevel(determineRisk(score))
            .reasons(reasons.toArray(new String[0]))  // List → String[]
            .build();
}
```

**Result:**
A single transaction gets scored with ALL applicable fraud reasons simultaneously. The `reasons` array in the result tells the operations team exactly WHY a transaction was flagged — making it easy to review and act on.

**Array vs List — why we start with List then convert:**
```java
// Array has fixed size — you must know the size upfront:
String[] reasons = new String[3]; // what if we have 2 reasons? 4 reasons?
reasons[0] = "HIGH_VALUE";
// Wasteful — guessing the size

// List is dynamic — grows as needed:
List<String> reasons = new ArrayList<>();
reasons.add("HIGH_VALUE");
reasons.add("HIGH_VELOCITY");  // can add as many as needed

// Convert to array at the end (API uses String[]):
reasons.toArray(new String[0])
```

---

## 5. Wrapper Classes

### STAR Answer — Wrapper Classes in Our Project

**Situation:**
Java generics (`List<T>`, `Optional<T>`, `Map<K,V>`) only work with objects — not primitives. Our banking code frequently switches between primitive values and their object equivalents.

**Task:**
Use wrapper classes correctly — especially for null-safe Redis operations and Kafka configuration.

**Action:**
```java
// ── Integer.MAX_VALUE in Kafka Producer Config ──────────────────────────────
// In KafkaProducerConfig.java:
config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
// = 2,147,483,647 retries — effectively "retry forever"
// Used because with idempotent producer, infinite retries are safe
// Can't use int.MAX_VALUE — Map.put() requires Object, not primitive

// ── Boolean.TRUE.equals() — null-safe Redis check ──────────────────────────
// In AuthenticationFilter.java:
Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
// hasKey() returns Boolean (wrapper) — could be null if Redis connection fails

// WRONG — NullPointerException if Redis returns null:
if (isBlacklisted == true) { ... }

// CORRECT — Boolean.TRUE.equals() handles null gracefully:
if (Boolean.TRUE.equals(isBlacklisted)) {
    return unauthorized(exchange);
}
// Boolean.TRUE.equals(null) → false (no NPE)
// Boolean.TRUE.equals(Boolean.FALSE) → false
// Boolean.TRUE.equals(Boolean.TRUE) → true

// ── Autoboxing and Unboxing ─────────────────────────────────────────────────
// Autoboxing: primitive → wrapper (automatic)
int attempts = 5;
Integer attemptsObj = attempts;  // auto-boxed: new Integer(5)
Map<String, Integer> config = new HashMap<>();
config.put("maxAttempts", 5);    // 5 (int) auto-boxed to Integer

// Unboxing: wrapper → primitive (automatic)
int maxAttempts = config.get("maxAttempts");  // Integer → int unboxing

// Unboxing pitfall — NullPointerException:
Long count = redisTemplate.opsForValue().increment(velocityKey);
// count could be null (Redis connection failure)

// WRONG:
if (count > 20) { ... }  // unboxing null → NullPointerException!

// CORRECT:
if (count != null && count > MAX_TRANSACTIONS_PER_HOUR) { ... }
// Check null BEFORE unboxing

// ── Long for timestamps and IDs ──────────────────────────────────────────────
// In JWT claims:
.setIssuedAt(new Date())
.setExpiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
// System.currentTimeMillis() returns long (not Long)
// accessTokenExpirationMs is long (can't use int — milliseconds overflow int range)
// int max = 2,147,483,647 ms = ~24 days  ← not enough
// long max = 9,223,372,036,854,775,807 ms = ~292 million years ← plenty
```

**Result:**
- `Integer.MAX_VALUE` makes Kafka retry "forever" safely — using plain `int max` in the config `Map<String,Object>` would fail compilation
- `Boolean.TRUE.equals()` prevents production crashes when Redis has connection issues
- Correct use of `long` for timestamps prevents overflow that would corrupt JWT expiry dates

---
