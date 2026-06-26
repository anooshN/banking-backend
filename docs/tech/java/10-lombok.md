# Lombok

## What Is Lombok?

Lombok is a Java library that eliminates boilerplate code by generating it at compile time via annotations.

**Simple explanation:** You tell Lombok what you want (getters, setters, constructors) with an annotation, and it writes the code for you. The generated code is invisible in your source file but exists in the compiled `.class` file.

---

## @Data — The Workhorse

```java
@Data  // generates ALL of the following:
public class LoginRequest {
    private String email;
    private String password;
    private String mfaCode;

    // Lombok generates:
    // public String getEmail() { return email; }
    // public void setEmail(String email) { this.email = email; }
    // public String getPassword() { ... }
    // public void setPassword(String password) { ... }
    // public String getMfaCode() { ... }
    // public void setMfaCode(String mfaCode) { ... }
    //
    // public boolean equals(Object o) { ... }  // compares all fields
    // public int hashCode() { ... }            // based on all fields
    // public String toString() { ... }         // "LoginRequest(email=..., password=..., mfaCode=...)"
}

// Without Lombok: 60+ lines of boilerplate
// With Lombok: 5 lines
```

**Warning:** Never use `@Data` on JPA entities. The generated `equals()` and `hashCode()` based on all fields causes issues with Hibernate's entity management. Use `@Getter @Setter` instead on entities, or use `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`.

---

## @Builder — Builder Pattern

```java
@Builder
@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String userId;
    private String email;
    private List<String> roles;
    private boolean mfaRequired;

    // Lombok generates:
    // public static AuthResponseBuilder builder() { ... }
    // class AuthResponseBuilder {
    //     public AuthResponseBuilder accessToken(String accessToken) { ... }
    //     public AuthResponseBuilder refreshToken(String refreshToken) { ... }
    //     ... one method per field ...
    //     public AuthResponse build() { ... }
    // }
}

// Usage:
AuthResponse response = AuthResponse.builder()
        .accessToken("eyJhbGci...")
        .refreshToken("eyJhbGci...")
        .tokenType("Bearer")
        .expiresIn(900)
        .userId(user.getId().toString())
        .email(user.getEmail())
        .roles(List.copyOf(user.getRoles()))
        .mfaRequired(false)
        .build();
```

**@Builder.Default — set default values in builder:**
```java
@Builder
public class ApiResponse<T> {

    @Builder.Default          // without this, builder ignores field initializers
    private LocalDateTime timestamp = LocalDateTime.now();
    // Now: ApiResponse.builder().build() → timestamp is set to now
    // Without @Builder.Default: ApiResponse.builder().build() → timestamp is null
}
```

---

## @RequiredArgsConstructor — Dependency Injection

```java
@Service
@RequiredArgsConstructor  // generates constructor for ALL final fields
public class AccountService {

    private final AccountRepository accountRepository;   // ← final
    private final BankingEventProducer eventProducer;    // ← final
    private final RedisTemplate<String, Object> redis;   // ← final

    // Lombok generates:
    // public AccountService(AccountRepository accountRepository,
    //                       BankingEventProducer eventProducer,
    //                       RedisTemplate<String, Object> redis) {
    //     this.accountRepository = accountRepository;
    //     this.eventProducer = eventProducer;
    //     this.redis = redis;
    // }
    // Spring uses this constructor for dependency injection
}
```

**Why final + @RequiredArgsConstructor?**
- `final` = immutable, thread-safe
- Constructor injection = explicit dependencies
- Spring injects via the generated constructor
- Works perfectly with Spring's DI without any `@Autowired`

---

## @Slf4j — Logging

```java
@Slf4j  // generates: private static final Logger log = LoggerFactory.getLogger(AccountService.class);
@Service
public class AccountService {

    public Account createAccount(UUID userId, AccountType type) {
        log.info("Creating {} account for user: {}", type, userId);
        // ...
        log.debug("Account created with number: {}", account.getAccountNumber());
        log.warn("User {} has {} accounts, approaching limit", userId, count);
        log.error("Failed to create account for user: {}", userId, exception);
        // ↑ Last argument can be a Throwable — includes stack trace in log
    }
}

// Without @Slf4j — you'd write this on every class:
// private static final Logger log = LoggerFactory.getLogger(AccountService.class);
```

---

## @NoArgsConstructor, @AllArgsConstructor

```java
@Entity
@Data
@Builder
@NoArgsConstructor   // JPA requires a no-arg constructor
@AllArgsConstructor  // @Builder needs an all-arg constructor
public class Account {
    private UUID id;
    private String accountNumber;
    private BigDecimal balance;
    // ...
}

// @NoArgsConstructor generates:
// public Account() {}

// @AllArgsConstructor generates:
// public Account(UUID id, String accountNumber, BigDecimal balance, ...) {
//     this.id = id;
//     this.accountNumber = accountNumber;
//     this.balance = balance;
// }
// (Used internally by @Builder)
```

---

## @Value — Immutable Objects

```java
// For DTOs that should never change after creation
@Value  // all fields final, no setters, only getters
public class TransferSummary {
    String fromAccount;
    String toAccount;
    BigDecimal amount;
    String referenceNumber;
    LocalDateTime processedAt;

    // Lombok generates constructor with all fields (required for @Value)
    // Lombok does NOT generate setters (immutable)
}
```

---

## @SneakyThrows — Checked Exception Handling

```java
// Kafka consumer throws Exception but @KafkaListener doesn't declare it
// Without @SneakyThrows you'd need try-catch or declare throws Exception

@SneakyThrows  // wraps checked exceptions in RuntimeException transparently
@KafkaListener(topics = "banking.transaction.events")
public void processEvent(String event) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> payload = mapper.readValue(event, Map.class);
    // mapper.readValue() throws JsonProcessingException (checked)
    // @SneakyThrows wraps it in RuntimeException automatically
}
```

---

## Lombok on JPA Entities — Special Rules

```java
// CORRECT entity configuration with Lombok
@Entity
@Table(name = "accounts")
@Getter               // generate only getters
@Setter               // generate only setters
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)  // only id for equals/hashCode
@ToString(exclude = {"roles"})  // exclude collections to avoid LazyInitializationException
public class User {

    @Id
    @EqualsAndHashCode.Include  // only the ID determines equality
    private UUID id;

    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> roles;
}

// WHY NOT @Data on entities:
// @Data generates equals() based on ALL fields
// Hibernate uses a proxy for lazy loading
// proxy.equals(realEntity) would return false even for same DB row
// This breaks collections, sets, and cache lookups
```
