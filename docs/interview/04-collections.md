# Module 4 — Collections Framework

---

## 1. List — Ordered, Allows Duplicates

**Situation:**
We need to store accounts for a user, transactions for an account, roles for a user — all ordered, accessed by index or iterated.

**Task:**
Use List implementations appropriate for the access pattern — ArrayList for random access, LinkedList for frequent insertion.

**Action:**

```java
// services/account-service — AccountService.java
// ArrayList — used everywhere for database query results
public List<Account> getAccountsByUserId(UUID userId) {
    // findByUserId returns ArrayList internally (Spring Data's default)
    List<Account> accounts = accountRepository.findByUserId(userId);
    // Ordered by insert order from DB — consistent results
    return accounts;
}

// services/transaction-service — TransactionService.java
// List with sorting for transaction history
public PageResponse<Transaction> getTransactionHistory(UUID accountId, int page, int size) {
    Pageable pageable = PageRequest.of(
        page, size,
        Sort.by("createdAt").descending()  // newest first
    );
    Page<Transaction> page = transactionRepository.findByAccountId(accountId, pageable);
    return PageResponse.of(page);
    // page.getContent() returns List<Transaction>
}

// shared/security-lib — JwtTokenProvider.java
// List.copyOf() — immutable list from roles
public String generateAccessToken(String userId, String email, List<String> roles) {
    return Jwts.builder()
            .setSubject(userId)
            .addClaims(Map.of(
                "roles", roles   // List<String> serialized to JSON array in JWT
            ))
            .compact();
}

// services/auth-service — AuthService.java
// List.of() — immutable list for roles when creating user
List<String> roles = List.copyOf(user.getRoles());
// List.of() — fixed-size, null-disallowing, for known small sets:
private static final List<String> PUBLIC_PATHS = List.of(
    "/api/v1/auth/login", "/api/v1/auth/register", "/actuator/health"
);

// ArrayList usage in fraud detection reason building:
List<String> reasons = new ArrayList<>();  // grows dynamically
if (isHighValue) reasons.add("HIGH_VALUE_TRANSACTION");
if (isHighVelocity) reasons.add("HIGH_VELOCITY");
if (isNewIp) reasons.add("NEW_IP_ADDRESS");
// Then convert to array:
fraudScore.setReasons(reasons.toArray(new String[0]));
```

**List Comparison:**
```java
// ArrayList — backed by array, O(1) random access, O(n) insert at middle
List<Transaction> txns = new ArrayList<>();
txns.get(500);  // O(1) — direct index access
txns.add(0, newTxn);  // O(n) — shifts all elements right

// LinkedList — doubly linked, O(1) insert at ends, O(n) random access
// We DON'T use LinkedList for transaction history — random access needed
// We DO use it conceptually in Kafka's internal message queue
```

---

## 2. Set — No Duplicates, Unordered

**Situation:**
Users can have multiple roles but no duplicate roles — a user cannot be both ROLE_CUSTOMER twice. Sets enforce uniqueness automatically.

**Task:**
Use Set for the roles collection to guarantee uniqueness without manual duplicate checking.

**Action:**

```java
// services/auth-service — User.java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role")
private Set<String> roles;
// WHY Set not List?
// A user cannot have duplicate roles — Set enforces this at the data structure level
// No need to check "if roles doesn't already contain this role, add it"

// Creating a user with roles:
User user = User.builder()
        .email(request.getEmail())
        .roles(Set.of(BankingConstants.ROLE_CUSTOMER))
        // Set.of() — immutable, no duplicates allowed
        // Set.of("ROLE_CUSTOMER", "ROLE_CUSTOMER") — throws IllegalArgumentException
        .build();

// Checking roles — in AuthService:
if (user.getRoles().contains("ROLE_ADMIN")) {
    // Set.contains() = O(1) for HashSet — much faster than List.contains() O(n)
}

// LinkedHashSet — preserves insertion order when needed:
Set<String> orderedRoles = new LinkedHashSet<>();
orderedRoles.add("ROLE_CUSTOMER");
orderedRoles.add("ROLE_AUDITOR");
// Iterating gives: ROLE_CUSTOMER, ROLE_AUDITOR (insertion order preserved)

// In JWT claims extraction:
@SuppressWarnings("unchecked")
public List<String> extractRoles(String token) {
    return (List<String>) extractAllClaims(token).get("roles");
    // JWT stores as JSON array ["ROLE_CUSTOMER"] — comes back as List
    // We convert to Set when we need uniqueness guarantee
}
```

---

## 3. Map — Key-Value Pairs

**Situation:**
Kafka producer configuration, audit event payload, validation error messages all need key-value storage. Maps are the natural structure.

**Task:**
Use the right Map implementation — HashMap for fast lookup, LinkedHashMap for ordered iteration, TreeMap for sorted keys.

**Action:**

```java
// shared/kafka-lib — KafkaProducerConfig.java
// HashMap for configuration — order doesn't matter
Map<String, Object> config = new HashMap<>();
config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
config.put(ProducerConfig.ACKS_CONFIG, "all");
// O(1) put and get — 20 config entries, no performance concern

// shared/audit-lib — AuditAspect.java
// HashMap for audit event payload
Map<String, Object> auditPayload = new HashMap<>();
auditPayload.put("action", auditable.action());   // "DEBIT"
auditPayload.put("resource", auditable.resource()); // "Transaction"
auditPayload.put("userId", userId);
auditPayload.put("status", status);                // "SUCCESS" or "FAILURE"
auditPayload.put("durationMs", durationMs);
auditPayload.put("timestamp", LocalDateTime.now().toString());
eventProducer.publishEvent(BankingConstants.TOPIC_AUDIT_EVENTS, userId, auditPayload);

// shared/exception-lib — GlobalExceptionHandler.java
// Map for validation errors — field name → error message
Map<String, String> errors = new HashMap<>();
ex.getBindingResult().getAllErrors().forEach(error -> {
    String fieldName = ((FieldError) error).getField();
    String message = error.getDefaultMessage();
    errors.put(fieldName, message);
});
// Result: {"email": "must be valid email", "password": "minimum 8 characters"}

// Map.of() — immutable map for JWT claims
Map.of(
    "email", email,
    "roles", roles,
    "type", "ACCESS"
)
// Map.of() — immutable, up to 10 entries, throws NullPointerException on null key/value

// ConcurrentHashMap — thread-safe map (used in metrics):
// metrics/BankingMetrics.java
// Micrometer's registry uses ConcurrentHashMap internally
// Thread-safe: 200 Tomcat threads can all read metrics simultaneously

// getOrDefault — avoid null checks:
int retryCount = retryMap.getOrDefault(transactionId, 0);
// Instead of:
// Integer count = retryMap.get(transactionId);
// int retryCount = count != null ? count : 0;

// computeIfAbsent — create if not present:
// velocityMap.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();
```

---

## 4. Queue and Deque

**Situation:**
Kafka acts as a distributed queue for our events. Within the JVM, Spring's async executor uses a queue for pending tasks.

**Task:**
Understand how queue-based processing works in our system, and where Java's Queue interface appears.

**Action:**

```java
// @Async thread pool — internally uses LinkedBlockingQueue
// services/notification-service — AsyncConfig.java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(5);
executor.setMaxPoolSize(20);
executor.setQueueCapacity(100);  // ← LinkedBlockingQueue with capacity 100
// When all 20 threads busy: next tasks wait in this queue
// When queue full (100 pending): CallerRunsPolicy kicks in
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

// Spring Batch chunk processing — uses Queue internally
// Each chunk of 100 transactions is read into a List (acts as a queue)
// Processor drains the list, writer writes it, repeat

// ArrayDeque — used as a stack in our recursive processing:
Deque<String> processingStack = new ArrayDeque<>();
// push/pop operations for depth-first processing
// ArrayDeque is faster than Stack (synchronized) and LinkedList
```

---

## 5. Collections Utility Methods

**Situation:**
We need to create read-only views of collections, sort accounts by balance, find min/max values.

**Task:**
Use `Collections` utility class methods to manipulate collections without writing manual loops.

**Action:**

```java
// Read-only view — prevent modification after creation
// services/auth-service — AuthService.java
List<String> roles = Collections.unmodifiableList(new ArrayList<>(user.getRoles()));
// Throws UnsupportedOperationException if anyone tries to modify it

// List.of() is preferred in modern Java (Java 9+):
List<String> immutableRoles = List.of("ROLE_CUSTOMER");

// Sort accounts by balance — highest first:
List<Account> accounts = accountRepository.findByUserId(userId);
accounts.sort(Comparator.comparing(Account::getBalance).reversed());
// Or with Collections.sort():
Collections.sort(accounts, (a, b) -> b.getBalance().compareTo(a.getBalance()));

// Find account with highest balance:
Optional<Account> richestAccount = accounts.stream()
        .max(Comparator.comparing(Account::getBalance));
// Or: Collections.max(accounts, Comparator.comparing(Account::getBalance));

// Collections.frequency — count occurrences:
long activeAccounts = accounts.stream()
        .filter(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
        .count();
// Or: Collections.frequency(statusList, AccountStatus.ACTIVE);

// Shuffle — randomize card number generation (conceptual):
List<Integer> digits = new ArrayList<>(List.of(0,1,2,3,4,5,6,7,8,9));
Collections.shuffle(digits);  // randomize order
```

---

## 6. Iterator and ListIterator

**Situation:**
When modifying a List while iterating (removing processed payments from the outbox), ConcurrentModificationException occurs with for-each.

**Task:**
Use Iterator for safe removal during iteration.

**Action:**

```java
// WRONG — ConcurrentModificationException:
List<Payment> payments = paymentRepository.findByOutboxProcessedFalse();
for (Payment payment : payments) {
    if (publishAndConfirm(payment)) {
        payments.remove(payment);  // modifying list during for-each → exception
    }
}

// CORRECT — Iterator allows safe removal:
Iterator<Payment> iterator = payments.iterator();
while (iterator.hasNext()) {
    Payment payment = iterator.next();
    if (publishAndConfirm(payment)) {
        iterator.remove();  // safe — tells iterator to remove current element
    }
}

// In practice, we use the database flag instead of in-memory removal:
// payment.setOutboxProcessed(true);
// paymentRepository.save(payment);
// But Iterator is the correct answer for in-memory list modification

// ListIterator — bidirectional, can add elements:
ListIterator<Transaction> listIterator = transactions.listIterator(transactions.size());
// Start from end, iterate backwards (newest first without reversing):
while (listIterator.hasPrevious()) {
    Transaction txn = listIterator.previous();
    processTransaction(txn);
}
```

**Interview Questions:**

- Q: What is the difference between ArrayList and LinkedList?
  A: "In our banking project, we use ArrayList for transaction history — we need to access elements by index when paginating and the list is returned from database queries (fixed size). ArrayList's O(1) random access is perfect. LinkedList would be better if we were constantly inserting/removing from the middle, but that's not our pattern. The internal difference: ArrayList is backed by a resizable array, LinkedList is a doubly-linked chain of nodes. ArrayList wastes some memory (capacity > size), LinkedList wastes memory on node pointers."

- Q: When would you use a HashMap vs LinkedHashMap vs TreeMap?
  A: "In our Kafka producer config, we use HashMap — we don't care about insertion order, just fast O(1) lookup. In our audit event payload, we also use HashMap. If we needed to serialize properties in a consistent order (for debugging or deterministic tests), we'd use LinkedHashMap — it preserves insertion order. TreeMap would be used if we needed keys in sorted order — like sorting account numbers alphabetically. TreeMap operations are O(log n) vs O(1) for HashMap, so we only use it when sorted order is actually required."

- Q: What is the difference between fail-fast and fail-safe iterators?
  A: "ArrayList's iterator is fail-fast — if the collection is modified during iteration (adding/removing elements from another thread or the same thread), it throws ConcurrentModificationException. This protects against incorrect behavior. If we need to iterate and modify simultaneously, we use Iterator.remove() which is safe, or use CopyOnWriteArrayList which has a fail-safe iterator — it iterates over a snapshot copy. In our banking app, for concurrent access to shared lists, we use thread-safe alternatives or ConcurrentHashMap."
