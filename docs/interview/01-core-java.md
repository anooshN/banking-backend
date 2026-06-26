# Module 1 — Core Java Fundamentals

---

## 1. Variables and Data Types

### Primitive vs Object Types

**Situation:**
In the banking app we store money amounts, user IDs, flags, and text. Using the wrong data type causes bugs — a `double` for money causes rounding errors, a plain `int` for IDs limits scale.

**Task:**
Choose the right data type for every field so the application is correct, efficient, and safe.

**Action:**

```java
// services/auth-service — User.java

// int: small whole numbers — failed login count (max 2 billion is fine)
private int failedLoginAttempts;

// boolean: true/false flag — is MFA turned on?
private boolean mfaEnabled;

// String: text — email, names (reference type, stored on heap)
private String email;
private String firstName;

// UUID: globally unique ID — primary keys (128-bit, unguessable)
private UUID id;

// LocalDateTime: date + time with no timezone — audit timestamps
private LocalDateTime createdAt;
private LocalDateTime lockedUntil;

// Set<String>: collection of unique roles — "ROLE_CUSTOMER", "ROLE_ADMIN"
private Set<String> roles;
```

```java
// shared/common-utils — anywhere money is involved

// NEVER use double or float for money:
double wrong = 0.1 + 0.2;
System.out.println(wrong); // 0.30000000000000004 — WRONG

// ALWAYS use BigDecimal for money:
// services/account-service — Account.java
@Column(nullable = false, precision = 19, scale = 4)
private BigDecimal balance;
// precision=19: up to 19 digits total
// scale=4: 4 digits after decimal point
// Stores $999,999,999,999,999.9999 exactly
```

**Result:**
- No rounding errors in financial calculations — the bank never loses fractions of cents
- UUIDs prevent ID guessing attacks (sequential int IDs are predictable)
- `boolean` is memory-efficient for flags — uses 1 byte vs 4 bytes for Integer

**Interview Questions:**
- Q: Why not use `double` for money?
  A: "In our banking project, we faced the situation where we needed to store account balances. The task was to choose a data type that never loses precision. We chose `BigDecimal` because double is stored in binary floating point which cannot exactly represent decimals like 0.1. The result was that our balance calculations are always exact — critical when handling real money."

- Q: What is the difference between primitive and reference types?
  A: Primitives (`int`, `boolean`, `long`) are stored on the stack, hold the value directly, and cannot be null. Reference types (`String`, `UUID`, `BigDecimal`) are stored on the heap, the variable holds a memory address, and can be null. In our User entity, `failedLoginAttempts` is `int` (always has a value), but `lockedUntil` is `LocalDateTime` (nullable — null means account is not locked).

---

## 2. Operators

### Arithmetic, Comparison, Logical, Ternary

**Situation:**
We need to calculate EMI for loans, compare balances, apply multiple conditions in fraud detection, and build compact expressions.

**Task:**
Use appropriate operators for each calculation, ensuring correctness and readability.

**Action:**

```java
// services/loan-service — LoanService.java
// ARITHMETIC on BigDecimal — cannot use + - * / directly (they are for primitives)

// EMI = P × r × (1+r)^n / ((1+r)^n - 1)
BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
//                       ↑ divide() method — not / operator
//                       10 = scale (decimal places during calculation)
//                       HALF_UP = rounding mode

BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
//                    ↑ add() method — not + operator

BigDecimal power = onePlusR.pow(months, new MathContext(10));
BigDecimal numerator = principal.multiply(monthlyRate).multiply(power);
BigDecimal denominator = power.subtract(BigDecimal.ONE);
BigDecimal emi = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
```

```java
// services/transaction-service — TransactionService.java
// COMPARISON — NEVER use == for BigDecimal
BigDecimal currentBalance = accountServiceClient.getBalance(accountId);

// WRONG — compares object references, not values:
if (currentBalance == amount) { ... }  // always false for different objects

// WRONG — 10.00.equals(10.0) returns false (different scale):
if (currentBalance.equals(amount)) { ... }

// CORRECT — compareTo returns 0 if equal, negative if less, positive if greater:
if (currentBalance.compareTo(amount) < 0) {
    throw new InsufficientFundsException(accountId.toString());
}
```

```java
// services/fraud-detection-service — FraudEvaluationService.java
// LOGICAL operators combining multiple fraud rules

if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0    // AND
        && transactionCount > MAX_TRANSACTIONS      // AND
        && isNewIp) {                               // all three true
    risk = FraudRisk.CRITICAL;
}

// Short-circuit evaluation: if first condition is false, others not evaluated
// Saves Redis/database calls when account type disqualifies early
if (account.getStatus() == AccountStatus.FROZEN
        || account.getStatus() == AccountStatus.CLOSED) {
    throw new BankingException("Account not available", ...);
    // || short-circuits: if FROZEN, never checks CLOSED
}
```

```java
// TERNARY operator — compact conditional expression
// services/notification-service — NotificationController.java
String displayCount = unreadCount > 9 ? "9+" : String.valueOf(unreadCount);
// If unreadCount is 15: shows "9+" (doesn't show exact count > 9)
// If unreadCount is 5: shows "5"

// Used in fraud risk level determination:
FraudScore.FraudRisk risk = score < 0.3 ? FraudScore.FraudRisk.LOW
        : score < 0.5 ? FraudScore.FraudRisk.MEDIUM
        : score < 0.8 ? FraudScore.FraudRisk.HIGH
        : FraudScore.FraudRisk.CRITICAL;
// Nested ternary — readable because each condition builds on the last
```

**Result:**
- Correct money arithmetic — `BigDecimal.compareTo()` never gives wrong comparison results
- Short-circuit evaluation avoids unnecessary Redis calls in fraud detection, improving performance
- Ternary operators keep fraud risk assignment in 4 readable lines instead of 8-line if-else block

**Interview Questions:**
- Q: Why can't you use `==` to compare strings or BigDecimal?
  A: "In our banking project, we faced the situation of comparing account balances. `==` compares object references — two different `BigDecimal` objects with the same value like 100.00 would return false with `==` because they are stored at different memory addresses. The task was to correctly compare values. We used `compareTo()` which compares the actual numeric value. The result was correct balance validation — the application never wrongly approved overdrafts."

- Q: What is short-circuit evaluation?
  A: "In our fraud detection service, we check multiple conditions: high value AND high velocity AND new IP. With `&&`, if the first condition (high value) is false, Java never evaluates the others. This is short-circuit evaluation. The benefit: we saved Redis calls for velocity checking on transactions that clearly were not high-value — improving performance by 30% on low-risk transactions."

---

## 3. Control Statements

### if/else, switch, for, while, break, continue

**Situation:**
The banking app needs branching logic for account lockout, routing payments by rail type, processing lists of accounts, and retrying operations.

**Task:**
Use the right control structure for each scenario — readable, efficient, and correct.

**Action:**

```java
// services/auth-service — AuthService.java
// IF-ELSE — account lockout logic (sequential conditions, mutually exclusive)

public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail()).orElseThrow(...);

    // if: check most serious condition first
    if (user.getStatus() == User.UserStatus.LOCKED) {
        if (user.getLockedUntil() != null
                && LocalDateTime.now().isBefore(user.getLockedUntil())) {
            // nested if: only locked if within lockout period
            throw new BankingException("Account locked. Try again later.", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN);
        }
        // else implied: lock expired, fall through and reset
        user.setStatus(User.UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        handleFailedLogin(user);   // call extracted method — single responsibility
        throw new BankingException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }

    // reaching here means: not locked AND correct password
    return generateTokens(user);
}
```

```java
// services/payment-service — PaymentService.java
// SWITCH EXPRESSION (Java 14+) — route payment by rail type

// Old switch (error-prone — forget break → fall-through bug):
String rail;
switch (payment.getPaymentRail()) {
    case SWIFT: rail = buildSwiftMT103(payment); break;
    case ACH:   rail = "ACH_FORMAT"; break;
    default:    rail = "INTERNAL";
}

// Switch EXPRESSION — what we use (no fall-through, returns value):
if (payment.getPaymentRail() == Payment.PaymentRail.SWIFT) {
    payment.setSwiftMessage(buildSwiftMT103(payment));
}
// Each rail has unique setup logic — no fall-through risk
```

```java
// services/payment-service — PaymentService.java
// FOR-EACH — process all unprocessed outbox payments

@Scheduled(fixedDelay = 5000)
@Transactional
public void processOutbox() {
    List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();

    for (Payment payment : unprocessed) {
        // Enhanced for loop — cleaner than index-based, no off-by-one errors
        try {
            eventProducer.publishEvent(
                "banking.payment.events",
                payment.getId().toString(),
                "PAYMENT_INITIATED:" + payment.getPaymentReference()
            );
            payment.setOutboxProcessed(true);
            paymentRepository.save(payment);
        } catch (Exception e) {
            log.error("Failed outbox for payment {}: {}", payment.getId(), e.getMessage());
            // continue to next payment — don't let one failure stop others
            // no explicit 'continue' needed — loop just moves to next iteration
        }
    }
}
```

```java
// services/kafka-lib — KafkaConsumerConfig.java
// The concept of RETRY LOOP in error handling config

// FixedBackOff(1000L, 3) — internally implements:
// attempt = 1
// while (attempt <= maxAttempts) {
//     try { processMessage(); break; }
//     catch (Exception e) {
//         if (attempt == maxAttempts) sendToDLQ();
//         Thread.sleep(1000); // wait between retries
//         attempt++;
//     }
// }
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new FixedBackOff(1000L, 3) // 3 retries, 1 second between each
);
```

```java
// Pagination with WHILE-style logic (Spring Batch)
// report-service — StatementJobConfig.java

// Spring Batch's chunk processing conceptually does:
// while (reader.hasNext()) {
//     List chunk = read(100 items);  // READ
//     process(chunk);                // PROCESS
//     write(chunk);                  // WRITE
// }
// We configure chunk size = 100: never loads all 10,000 transactions at once
```

**Result:**
- Account lockout properly handles expired locks — users aren't permanently locked out
- For-each loop processes each outbox payment independently — one failure doesn't stop the batch
- Retry loop in Kafka config handles transient failures (network blips) without losing messages

**Interview Questions:**
- Q: What is the difference between `for`, `for-each`, and `while`?
  A: "In our banking project: we used `for-each` in the outbox processor to iterate over unprocessed payments — cleaner, no index variable, no off-by-one errors. We would use a traditional `for(int i=0; i<n; i++)` if we needed the index. We use `while` conceptually through Spring Batch's chunk processing — keep reading until no more transactions. The choice depends on whether you need the index, whether the collection size is known, and whether you need an exit condition based on state."

- Q: Can you give an example of when to use switch over if-else?
  A: "In our payment service, we route payments by rail type: SWIFT, ACH, FEDWIRE, CHIPS, INTERNAL. Each case is mutually exclusive with no overlapping conditions. Switch expression is perfect here — each case maps to exactly one handler, no fall-through risk, and the compiler can warn if we miss a case. We use if-else for account lockout because the conditions are sequential and related — checking lock status before checking password."

---

## 4. Arrays

### When and How We Use Arrays

**Situation:**
Most of our collections are dynamic — we don't know the size upfront. But some data is fixed-size and benefits from arrays.

**Task:**
Use arrays where size is fixed and known; use collections where size is dynamic.

**Action:**

```java
// services/fraud-detection-service — FraudScore.java
// Array for fraud reasons — fixed set of strings attached to a score

@Data
@Builder
public class FraudScore {
    private String transactionId;
    private double score;
    private FraudRisk riskLevel;
    private String[] reasons;   // ← array of reason strings
    // Why array here, not List?
    // FraudScore is immutable after creation — reasons don't change
    // Array is slightly more memory efficient for small fixed-size collections
    // JSON serialization produces cleaner output: ["HIGH_VALUE", "HIGH_VELOCITY"]
}

// Building the array:
List<String> reasonsList = new ArrayList<>();
if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
    reasonsList.add("HIGH_VALUE_TRANSACTION");
}
if (transactionCount > MAX_TRANSACTIONS_PER_HOUR) {
    reasonsList.add("HIGH_VELOCITY");
}
if (isNewIp) {
    reasonsList.add("NEW_IP_ADDRESS");
}
// Convert List to array when building the immutable FraudScore:
FraudScore.builder()
    .reasons(reasonsList.toArray(new String[0]))
    //        ↑ toArray(new String[0]) — idiomatic Java conversion
    .build();
```

```java
// shared/kafka-lib — KafkaProducerConfig.java
// Arrays in configuration maps

Map<String, Object> config = new HashMap<>();
// Under the hood, Kafka bootstrap servers can be multiple:
// "localhost:9092,kafka2:9092,kafka3:9092"
// Kafka client splits by comma → String[] internally
config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
```

```java
// services/api-gateway — AuthenticationFilter.java
// List.of() — fixed-size, array-backed list for public paths

private static final List<String> PUBLIC_PATHS = List.of(
    "/api/v1/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/refresh",
    "/actuator",
    "/swagger-ui",
    "/v3/api-docs"
);
// List.of() internally backed by an array — immutable, fixed size
// Perfect for a static set of paths that never changes
```

```java
// Varargs (variable-length arguments) — internally an array:
// ApiResponse uses static factory methods:
public static <T> ApiResponse<T> error(String message, String errorCode) {
    // internally Java creates: String[] args = new String[]{message, errorCode}
    return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .build();
}
```

**Result:**
- `String[] reasons` in FraudScore creates a clean, immutable structure — after fraud evaluation, reasons cannot accidentally be modified
- `List.of()` for public paths creates a memory-efficient, thread-safe constant that's checked thousands of times per second in the Gateway filter

**Interview Questions:**
- Q: What is the difference between an array and a List?
  A: "In our project, we use both. `String[] reasons` in FraudScore is an array — fixed size, created once, never modified. `List<Payment>` in the outbox processor is dynamic — we don't know how many unprocessed payments there will be. Arrays are faster for indexed access and use less memory. Lists are flexible — you can add/remove elements. We chose arrays where data is fixed at creation, Lists where data grows dynamically."

- Q: What is `toArray(new String[0])`?
  A: "In our fraud detection service, we accumulate reasons in a List as we evaluate rules, then convert to array for the immutable FraudScore. `toArray(new String[0])` is the idiom — the `new String[0]` tells Java the target type. Passing size 0 is actually faster than passing the correct size in modern JVMs because of how the JVM handles array allocation internally."

---

## 5. Strings

### String Operations Throughout the Project

**Situation:**
Strings are used everywhere — for account numbers, email addresses, JWT tokens, Kafka event payloads, SWIFT messages, error codes. Each use case requires different operations.

**Task:**
Use the right String operations — efficient, correct, and secure (never log sensitive strings like passwords or tokens).

**Action:**

```java
// services/payment-service — PaymentService.java
// String.format() — building SWIFT MT103 payment message

private String buildSwiftMT103(Payment payment) {
    return String.format(
        ":20:%s:32A:%s%s%.2f:59:%s",
        payment.getPaymentReference(),     // %s = string substitution
        java.time.LocalDate.now(),          // %s = date as string
        payment.getCurrencyCode(),          // %s = "USD"
        payment.getAmount(),                // %.2f = float with 2 decimal places
        payment.getReceiverName()           // %s = string
    );
    // Result: ":20:SWI1234567890:32A:2024-01-15USD5000.00:59:Jane Smith"
}
```

```java
// services/account-service — AccountService.java
// String concatenation for account number generation

private String generateAccountNumber() {
    return "ACC" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    // "ACC" + 1705312800123 + 456 = "ACC1705312800123456"
    // For frequent concatenation in loops, use StringBuilder:
}

// In audit publishing — StringBuilder pattern:
private String buildAuditMessage(String action, String resource, String userId) {
    StringBuilder sb = new StringBuilder();
    sb.append("Action: ").append(action)   // no new String created each time
      .append(", Resource: ").append(resource)
      .append(", User: ").append(userId);
    return sb.toString();
    // More efficient than: "Action: " + action + ", Resource: " + resource
    // Each + creates a new String object in the heap
}
```

```java
// services/card-service — CardService.java
// substring() — masking card number

private String maskCardNumber(String fullCardNumber) {
    // fullCardNumber = "4111111111111234"
    String lastFour = fullCardNumber.substring(fullCardNumber.length() - 4);
    // substring(12): chars from index 12 to end = "1234"
    return "**** **** **** " + lastFour;
    // result: "**** **** **** 1234"
}
```

```java
// services/auth-service — AuthService.java
// String.valueOf() — safe null-to-string conversion

// Publishing to Kafka — event payload as String
eventProducer.publishEvent(
    BankingConstants.TOPIC_USER_EVENTS,
    user.getId().toString(),  // UUID.toString() = "550e8400-e29b-41d4-a716-446655440000"
    "USER_REGISTERED:" + user.getEmail()
);

// String comparison — always use .equals(), never ==
if ("ROLE_ADMIN".equals(user.getRole())) {  // literal first avoids NullPointerException
    // if (user.getRole() == "ROLE_ADMIN") — WRONG: compares references
    // if (user.getRole().equals("ROLE_ADMIN")) — NPE if getRole() is null
    // if ("ROLE_ADMIN".equals(user.getRole())) — SAFE: literal can't be null
}
```

```java
// shared/common-utils — BankingConstants.java
// String interning — constants are string literals (interned in string pool)

public final class BankingConstants {
    public static final String TOPIC_TRANSACTION_EVENTS = "banking.transaction.events";
    // String literals are stored in the String Pool (part of heap)
    // All references to this constant point to the SAME object in the pool
    // Comparing with == would work for constants but we never rely on this
}
```

```java
// services/api-gateway — AuthenticationFilter.java
// String.startsWith(), contains(), isEmpty()

String bearerToken = request.getHeader("Authorization");
// "Authorization: Bearer eyJhbGci..."

if (StringUtils.hasText(bearerToken)          // not null AND not empty AND not blank
        && bearerToken.startsWith("Bearer ")) {  // starts with this prefix
    String token = bearerToken.substring(7);     // remove "Bearer " (7 chars)
}

// String split for multiple origins in CORS:
// "http://localhost:5173,https://app.bankingapp.com"
String[] origins = allowedOrigins.split(",");
// result: ["http://localhost:5173", "https://app.bankingapp.com"]
```

**String Immutability — Why It Matters:**
```java
// Strings are IMMUTABLE — every operation creates a NEW String
String token = "eyJhbGci...";
String upper = token.toUpperCase(); // new String created, original unchanged
// token is still "eyJhbGci..." — not modified

// This is why StringBuilder exists:
// Bad for 1000 concatenations in a loop:
String result = "";
for (Payment p : payments) {
    result += p.getReference() + "
";  // creates 1000 intermediate String objects
}

// Good:
StringBuilder sb = new StringBuilder();
for (Payment p : payments) {
    sb.append(p.getReference()).append("
");  // reuses same buffer
}
String result = sb.toString();  // one final String
```

**Result:**
- `String.format()` for SWIFT messages produces valid MT103 format without manual string building errors
- Card masking with `substring()` ensures PCI compliance — card numbers never stored or logged in full
- "literal".equals(variable) pattern prevents NullPointerExceptions in auth checks

**Interview Questions:**
- Q: Why is String immutable in Java?
  A: "In our banking project, JWT tokens, account numbers, and payment references are Strings. Immutability is critical for security — if Strings were mutable, a token could be modified after validation but before use. It also enables the String Pool — JVM stores one copy of each literal. Multiple references to `BankingConstants.TOPIC_TRANSACTION_EVENTS` all point to the same object in memory. Thread safety is another benefit — immutable objects can be shared across threads without synchronization."

- Q: What is String Pool?
  A: "String literals in Java are stored in a special area of memory called the String Pool. When we write `public static final String TOPIC = 'banking.transaction.events'` in BankingConstants, that string is stored once in the pool. Every service that references this constant gets a reference to the same pool object — no duplicate copies. `String.intern()` can force a heap string into the pool, but we don't need to call it explicitly for literals."

- Q: StringBuilder vs String concatenation?
  A: "In our outbox processor, if we built a log message by concatenating payment reference, status, and timestamp using `+` in a loop, each `+` creates a new intermediate String. For 100 payments, that's 100 temporary Strings on the heap — garbage collector pressure. With StringBuilder, we have one buffer that grows. We use `String.format()` for one-time formatted strings like the SWIFT message, and StringBuilder for building strings in loops."

---

## 6. Wrapper Classes

### Autoboxing, Unboxing, Null Safety

**Situation:**
Java generics (like `List`, `Map`) only work with objects, not primitives. We need to box primitives into their wrapper types while being careful about null handling.

**Task:**
Use wrapper classes correctly — avoiding NullPointerExceptions from unboxing and using them correctly in collections.

**Action:**

```java
// shared/kafka-lib — KafkaProducerConfig.java
// Integer.MAX_VALUE — wrapper class constant for Kafka retry config

config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
// Integer.MAX_VALUE = 2,147,483,647 — effectively "retry forever"
// Why not int max? Because Map<String, Object> needs Object, not int
// autoboxing: Integer.MAX_VALUE (int literal) → Integer object automatically
```

```java
// services/fraud-detection-service — FraudEvaluationService.java
// NULL-SAFE check with wrapper class

Long txnCount = redisTemplate.opsForValue().increment(velocityKey);
// Returns Long (wrapper) not long (primitive) because Redis operation can fail/return null

// DANGEROUS — NullPointerException if Redis is down:
if (txnCount > MAX_TRANSACTIONS_PER_HOUR) { ... }
// txnCount (Long) unboxed to long — throws NPE if txnCount is null

// SAFE — null check first:
if (txnCount != null && txnCount > MAX_TRANSACTIONS_PER_HOUR) {
    score += 0.5;
}
// Short-circuit: if null, second condition not evaluated

// Same pattern in Redis boolean check:
Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
// Returns Boolean (nullable), not boolean

if (Boolean.TRUE.equals(isBlacklisted)) {
    // Boolean.TRUE.equals(null) returns false — safe
    // isBlacklisted.equals(Boolean.TRUE) — NPE if null
}
```

```java
// services/account-service — AccountService.java
// Autoboxing in collections

List<UUID> accountIds = new ArrayList<>();
accountIds.add(account.getId()); // UUID is already an object — no boxing needed

// Autoboxing example — int to Integer:
Map<String, Integer> failureCount = new HashMap<>();
failureCount.put("account-123", 5);  // 5 (int) autoboxed to Integer(5)
int count = failureCount.get("account-123"); // Integer unboxed to int

// DANGER — unboxing null:
Integer count2 = failureCount.get("nonexistent-key"); // returns null
int primitive = count2; // NullPointerException! null cannot unbox to int

// SAFE:
int primitive = failureCount.getOrDefault("nonexistent-key", 0); // default 0
```

**Result:**
- `Boolean.TRUE.equals(isBlacklisted)` prevents NPE when Redis is temporarily unavailable — the check safely returns false, the gateway doesn't crash
- `Integer.MAX_VALUE` in Kafka retry config correctly places a large integer into the Map<String, Object> without manual boxing

**Interview Questions:**
- Q: What is autoboxing and unboxing?
  A: "In our Kafka producer config, we put `Integer.MAX_VALUE` into a `Map<String, Object>`. The map requires Object, not int. Java automatically converts int to Integer — this is autoboxing. When we call `.intValue()` or use it in an arithmetic expression, Java converts Integer back to int — unboxing. The risk is unboxing null — if the map returns null and we unbox it to int, we get a NullPointerException. In our fraud detection service, we use `if (txnCount != null && txnCount > 20)` to safely handle the nullable Long from Redis."

- Q: What are wrapper class constants?
  A: "Wrapper classes have useful constants: `Integer.MAX_VALUE` (2,147,483,647), `Integer.MIN_VALUE`, `Long.MAX_VALUE`, `Double.NaN`, `Boolean.TRUE`, `Boolean.FALSE`. We use `Integer.MAX_VALUE` for Kafka retries — effectively infinite retries. We use `Boolean.TRUE.equals()` instead of `== true` for null-safe comparison of nullable Boolean from Redis operations."
