# Module 5 — Generics

---

## 1. What Are Generics and Why We Use Them

**Situation:**
Every API endpoint returns a response. Without generics, we'd need `AccountResponse`, `TransactionResponse`, `PaymentResponse` — identical classes except for the data type inside.

**Task:**
Write one `ApiResponse` class that works for ANY data type while keeping full type safety at compile time.

**Action:**

```java
// shared/common-utils — ApiResponse.java
// T = Type Parameter — a placeholder for the actual type
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    // T can be: Account, List<Transaction>, Map<String,String>, Void, or anything
    private boolean success;
    private String message;
    private T data;              // ← T: the actual type determined at usage
    private String errorCode;
    private LocalDateTime timestamp;

    // Static factory method — T is inferred from the argument
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)  // compiler knows data's type is T
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
        // T is Void here typically — no data on error
    }
}

// USAGE — T is inferred at the call site:
// Returns ApiResponse<Account>:
return ResponseEntity.ok(ApiResponse.success(account));

// Returns ApiResponse<List<Transaction>>:
return ResponseEntity.ok(ApiResponse.success(transactions));

// Returns ApiResponse<Map<String, String>>:
return ResponseEntity.ok(ApiResponse.success(validationErrors));

// Returns ApiResponse<Void>:
return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));

// WITHOUT GENERICS — would need:
public class AccountApiResponse { private Account data; ... }
public class TransactionListApiResponse { private List<Transaction> data; ... }
// Duplicated code for every response type — unmaintainable
```

---

## 2. PageResponse<T> — Generic Pagination

**Situation:**
Both transactions AND notifications need paginated responses. The page metadata (total pages, total elements) is identical — only the content type differs.

**Task:**
One `PageResponse<T>` class that works for any entity type.

**Action:**

```java
// shared/common-utils — PageResponse.java
@Data
@Builder
public class PageResponse<T> {
    private List<T> content;         // T: actual data items
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;

    // Static factory — converts Spring's Page<T> to our PageResponse<T>
    // The <T> before the return type means: this method itself is generic
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())      // List<T> — same T
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }
}

// USAGE:
// Transaction history:
Page<Transaction> txnPage = transactionRepository.findByAccountId(accountId, pageable);
PageResponse<Transaction> response = PageResponse.of(txnPage);
// response.getContent() returns List<Transaction>

// Notification list:
Page<Notification> notifPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
PageResponse<Notification> response = PageResponse.of(notifPage);
// Same PageResponse class, different T
```

---

## 3. Generic Repository Interfaces

**Situation:**
Every service has a repository that needs save, findById, findAll, delete. Writing these from scratch for every entity is repetitive.

**Task:**
Use Spring Data's generic repository interfaces — parameterized with entity type and ID type.

**Action:**

```java
// Spring Data JPA:
// JpaRepository<Entity, IdType>
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // JpaRepository<T, ID> where:
    // T = Account (the entity type)
    // ID = UUID (the primary key type)

    // We get for FREE, all type-safe:
    Account save(Account account);           // not Object, specifically Account
    Optional<Account> findById(UUID id);     // not Object, specifically UUID → Account
    List<Account> findAll();                 // not List<Object>, specifically List<Account>
    void deleteById(UUID id);                // type-safe delete

    // Our additions — still type-safe:
    List<Account> findByUserId(UUID userId); // returns List<Account>, not List<Object>
}

// MongoDB:
public interface NotificationRepository extends MongoRepository<Notification, String> {
    // MongoRepository<T=Notification, ID=String>
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    // Returns Page<Notification> — type-safe
}

// Cassandra:
public interface AuditLogRepository extends CassandraRepository<AuditLog, UUID> {
    List<AuditLog> findRecentByUserId(String userId);
    // All type-safe — compiler catches if you accidentally pass Account where AuditLog expected
}
```

---

## 4. Generic Methods

**Situation:**
Some utility methods work with any type — like our `ApiResponse.success()` which should work for Account, List<Transaction>, anything.

**Task:**
Write methods with their own type parameter, independent of the class.

**Action:**

```java
// The <T> before return type = this method has its own type parameter
public static <T> ApiResponse<T> success(T data) {
    // Compiler infers T from the argument:
    // ApiResponse.success(account)       → T is Account
    // ApiResponse.success(transactions)  → T is List<Transaction>
    // ApiResponse.success(null, "done")  → T is inferred as Void or Object
}

public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .build();
}

// PageResponse.of() — generic static method:
public static <T> PageResponse<T> of(Page<T> page) {
    // T in the input Page<T> matches T in the output PageResponse<T>
    // Compiler ensures you don't pass Page<Transaction> and expect PageResponse<Account>
}

// Bounded type parameter — T must be a Comparable:
public static <T extends Comparable<T>> T findMax(List<T> list) {
    return list.stream().max(Comparator.naturalOrder()).orElseThrow();
}
// findMax(List<BigDecimal>) — BigDecimal implements Comparable, allowed
// findMax(List<Account>) — Account doesn't implement Comparable, compile error

// Wildcard — ? (unknown type):
public void printAllIds(List<? extends BaseEntity> entities) {
    // ? extends BaseEntity: accepts List<Account>, List<Transaction>, etc.
    // but NOT List<String>
    entities.forEach(e -> log.info("ID: {}", e.getId()));
}
```

---

## 5. Type Erasure and Raw Types

**Situation:**
JWT claims come back as `Object` from the JWT library — we must cast them to the right type. Understanding type erasure explains why we get unchecked cast warnings.

**Task:**
Understand type erasure and handle generic casts safely.

**Action:**

```java
// shared/security-lib — JwtTokenProvider.java
// Type erasure: at RUNTIME, List<String> becomes just List
// The generic type information is erased — JVM only sees List

@SuppressWarnings("unchecked")
public List<String> extractRoles(String token) {
    // extractAllClaims returns Claims (a Map-like object)
    // get("roles") returns Object — the JWT library doesn't know generics
    Object rolesObj = extractAllClaims(token).get("roles");

    // We CAST to List<String> — this is an "unchecked cast"
    // At runtime: actually just List (type erasure)
    // If the JWT contained something other than strings, we'd get ClassCastException later
    return (List<String>) rolesObj;
    // @SuppressWarnings("unchecked") — tells compiler "we know about this cast"
}

// SAFER approach using instanceof:
public List<String> extractRoles(String token) {
    Object rolesObj = extractAllClaims(token).get("roles");
    if (rolesObj instanceof List<?> list) {  // wildcard: List of any type
        return list.stream()
                .filter(item -> item instanceof String)
                .map(item -> (String) item)
                .collect(Collectors.toList());
    }
    return Collections.emptyList();
}

// Why @SuppressWarnings("unchecked") is needed:
// Generic type List<String> is erased to List at runtime
// The cast (List<String>) cannot be verified at runtime
// It's a "trust me" cast — we trust the JWT contained strings
// Without @SuppressWarnings: compiler warns about every such cast
```

**Interview Questions:**

- Q: What are generics and why were they introduced in Java?
  A: "In our banking project, before generics we'd need AccountApiResponse, TransactionApiResponse, and PaymentApiResponse — three identical classes. Generics were introduced in Java 5 to enable type-safe collections and classes. We have one ApiResponse<T> class that works for any type. The benefit: compile-time type checking. If you write ApiResponse<Account> and accidentally put a Transaction inside, the compiler catches it — no ClassCastException at runtime. Before generics, everything was stored as Object and casts were unchecked."

- Q: What is type erasure?
  A: "Type erasure is how Java implements generics — generic type information is removed at compile time and replaced with Object (or the bounded type). At runtime, `List<Transaction>` and `List<Account>` are both just `List`. This is why we can't do `if (list instanceof List<String>)` — at runtime there's no String type info. In our JWT token provider, we cast `(List<String>)` from the claims map — this is an unchecked cast because the JVM can't verify the generic type at runtime due to erasure. We use `@SuppressWarnings('unchecked')` and trust the JWT structure."

- Q: What is the difference between `List<?>`, `List<Object>`, and `List<T>`?
  A: "In our project: `List<T>` is used in generic methods like `PageResponse.of()` where T is determined at call time — type-safe and connected. `List<?>` is a wildcard — you can read from it but not add to it (except null). We use it when we want to accept any typed List: `List<? extends BankingException>` accepts List<ResourceNotFoundException> and List<InsufficientFundsException>. `List<Object>` accepts only `List<Object>` — does NOT accept `List<Account>` due to invariance. `List<Object>` is almost never what you want when you think you want 'any list'."
