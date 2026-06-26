# Module 02 — OOP: Object-Oriented Programming

---

## 1. Classes & Objects

### STAR Answer

**Situation:**
Our banking project needed to model real-world banking concepts — accounts, transactions, payments, users, cards, loans — and pass them between controllers, services, databases, and Kafka events.

**Task:**
Design Java classes that accurately represent each banking concept, with proper data, behaviour, and encapsulation.

**Action:**
```java
// Entity class — maps to a database table, represents a real concept:
@Entity
@Table(name = "accounts")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {
    // State (what an account HAS):
    private UUID id;
    private String accountNumber;
    private UUID userId;
    private AccountType accountType;
    private BigDecimal balance;
    private AccountStatus status;

    // Behavior (what an account KNOWS how to do) is in AccountService
    // Entities = pure data, no business logic
}

// Service class — contains behavior:
@Service
@RequiredArgsConstructor
public class AccountService {
    // Behavior (what the system can DO with accounts):
    public Account createAccount(UUID userId, AccountType type, String currency) { ... }
    public Account getAccountById(UUID accountId) { ... }
    public Account updateBalance(UUID accountId, BigDecimal amount) { ... }
    public Account freezeAccount(UUID accountId) { ... }
}

// DTO class — transfers data between layers (no database mapping):
@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    // Only the fields the user sends — no id, no createdAt
}

// Objects are created from classes:
Account account = Account.builder()
        .userId(userId)
        .accountType(AccountType.CHECKING)
        .balance(BigDecimal.ZERO)
        .status(AccountStatus.ACTIVE)
        .build();
// 'account' is an object — a specific instance of the Account class
```

**Result:**
Clear separation: Entity classes model data, Service classes model behaviour, DTO classes model API contracts. Every team member knows exactly which class to look in for what.

---

## 2. Encapsulation

### STAR Answer

**Situation:**
In our banking app, sensitive data like passwords, JWT secrets, card PIN hashes, and account balances must never be directly accessible or modifiable from outside their class. If any code could directly set `user.password = "hacked"`, security would collapse.

**Task:**
Ensure that internal data is protected. No external code can directly access or modify sensitive fields without going through validated, controlled methods.

**Action:**
```java
// In User entity — fields are private, access only through methods:
@Entity
public class User {
    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;    // stores BCrypt hash, never plaintext

    private User.UserStatus status;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;

    // Lombok @Data generates getters AND setters
    // But we control WHICH setters are called and WHERE
}

// In AuthService — the ONLY place that touches password:
@Service
public class AuthService {
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request) {
        User user = User.builder()
                // Password is ALWAYS hashed before storage
                // No other code can bypass this — it goes through this service
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        // Password is ALWAYS compared via BCrypt — never stored/logged plaintext
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BankingException("Invalid credentials", ...);
        }
    }
}

// In JwtTokenProvider — secret key is encapsulated:
@Component
public class JwtTokenProvider {
    @Value("${banking.jwt.secret}")
    private String jwtSecret;   // private — nobody outside can access this

    // Only this class creates/validates tokens using the secret
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
        // The Key object is created fresh each time — never exposed
    }

    // Public interface — other classes call these, never access the secret directly:
    public String generateAccessToken(String userId, String email, List<String> roles) { ... }
    public boolean validateToken(String token) { ... }
    public String extractUserId(String token) { ... }
}
```

**Result:**
Even if a developer accidentally writes code that tries to set a password directly, the architecture forces them to go through `AuthService.register()` — which always hashes it. The JWT secret is never accessible outside `JwtTokenProvider`. This is enforced by design, not just convention.

---

## 3. Inheritance

### STAR Answer

**Situation:**
Our banking app throws many types of exceptions — resource not found, insufficient funds, account locked, service unavailable, validation failed. Each has a different HTTP status code and error code. Without a hierarchy, we'd write duplicate error-handling code for every exception type.

**Task:**
Create an exception hierarchy so all banking exceptions share common behaviour (HTTP status, error code) while each type can add specific details.

**Action:**
```java
// BASE CLASS — defines the common structure all banking exceptions share:
public class BankingException extends RuntimeException {
    // These fields are common to ALL banking exceptions:
    private final String errorCode;      // "INSUFFICIENT_FUNDS", "ACCOUNT_LOCKED", etc.
    private final HttpStatus httpStatus; // 400, 401, 403, 404, 422, 503

    // Constructor — every subclass must provide these values:
    public BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);           // calls RuntimeException(message)
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // With cause — for wrapping lower-level exceptions:
    public BankingException(String message, String errorCode,
                             HttpStatus httpStatus, Throwable cause) {
        super(message, cause);    // chains the original exception
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

// SUBCLASS 1 — no extra fields, just specific message and codes:
public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String resource, String id) {
        super(
            String.format("%s not found with id: %s", resource, id),  // message
            "RESOURCE_NOT_FOUND",           // errorCode
            HttpStatus.NOT_FOUND            // 404
        );
        // Calls BankingException constructor automatically
        // No need to repeat the HTTP status handling logic
    }
}

// SUBCLASS 2 — specific to money operations:
public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId) {
        super(
            String.format("Insufficient funds in account: %s", accountId),
            "INSUFFICIENT_FUNDS",
            HttpStatus.UNPROCESSABLE_ENTITY  // 422 — valid request, can't process
        );
    }
}

// Usage — clean, semantic, consistent:
// In AccountService:
Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
// Throws 404 with errorCode="RESOURCE_NOT_FOUND"

// In TransactionService:
if (currentBalance.compareTo(amount) < 0) {
    throw new InsufficientFundsException(accountId.toString());
    // Throws 422 with errorCode="INSUFFICIENT_FUNDS"
}

// In GlobalExceptionHandler — ONE handler catches ALL:
@ExceptionHandler(BankingException.class)     // catches BankingException AND all subclasses
public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
    return ResponseEntity
            .status(ex.getHttpStatus())       // gets the status from whatever subclass it is
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
}
```

**Result:**
- `GlobalExceptionHandler` has ONE method that handles ALL banking exceptions through inheritance — not 10 separate handlers
- Adding a new exception type (e.g., `DailyLimitExceededException`) requires only 5 lines extending `BankingException`
- Every exception automatically has proper HTTP status, error code, and message formatting

---

## 4. Polymorphism

### STAR Answer

**Situation:**
Our `account-service` calls `transaction-service` using Feign (HTTP). But in tests, we can't call real services. And if `transaction-service` is down in production, we need a safe fallback behaviour — not a crash.

**Task:**
Write code that works with different implementations of the same interface — the real HTTP client in production, a mock in tests, and a fallback when the service is down.

**Action:**
```java
// INTERFACE — defines the CONTRACT (what methods exist):
@FeignClient(name = "account-service", fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {
    @GetMapping("/api/v1/accounts/{accountId}/balance")
    BigDecimal getBalance(@PathVariable UUID accountId);

    @PatchMapping("/api/v1/accounts/{accountId}/balance")
    void updateBalance(@PathVariable UUID accountId, @RequestParam BigDecimal amount);
}

// IMPLEMENTATION 1 — Feign generates this at runtime (real HTTP calls):
// Feign creates a proxy that makes actual HTTP requests to account-service
// This implementation is invisible in code — Feign generates it

// IMPLEMENTATION 2 — Fallback (when account-service is down):
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public BigDecimal getBalance(UUID accountId) {
        // Called automatically when circuit breaker is OPEN or service times out
        throw new BankingException(
            "Account service temporarily unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Override
    public void updateBalance(UUID accountId, BigDecimal amount) {
        throw new BankingException(
            "Account service temporarily unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}

// IMPLEMENTATION 3 — Mock in tests:
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    AccountServiceClient accountServiceClient;  // Mockito creates a fake implementation

    @Test
    void debit_sufficientBalance_success() {
        // Tell the mock what to return:
        when(accountServiceClient.getBalance(accountId))
                .thenReturn(new BigDecimal("5000.00"));

        // TransactionService uses AccountServiceClient interface
        // It doesn't know or care if it's the real Feign client, fallback, or mock
        Transaction txn = transactionService.debit(accountId, userId,
                new BigDecimal("100.00"), "Test", "corr-1");

        assertThat(txn.getTransactionType()).isEqualTo(Transaction.TransactionType.DEBIT);
    }
}

// TransactionService uses the INTERFACE — polymorphism in action:
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final AccountServiceClient accountServiceClient;
    // Type is the interface — same code works with ALL implementations above

    public Transaction debit(UUID accountId, ...) {
        BigDecimal balance = accountServiceClient.getBalance(accountId);
        // In production: real Feign HTTP call
        // In tests: mock returns preset value
        // When service down: fallback throws SERVICE_UNAVAILABLE
        // TransactionService code is IDENTICAL in all three cases
    }
}
```

**Result:**
- Production code works with real Feign HTTP calls
- Same code runs in tests with mocks (no real network needed)
- Same code degrades gracefully with the fallback when account-service is down
- No `if (isProd) ... else if (isTest) ...` conditional logic anywhere

---

## 5. Abstraction

### STAR Answer

**Situation:**
Our services interact with databases (PostgreSQL, MongoDB, Cassandra), message brokers (Kafka), and external services (SMTP, S3). The business logic — how a loan EMI is calculated, how fraud is scored — should not need to know the SQL, the Kafka topic format, or the S3 bucket structure.

**Task:**
Abstract away infrastructure details so business logic is clean and testable, while infrastructure concerns are hidden behind interfaces.

**Action:**
```java
// ABSTRACTION via interface — AccountRepository hides ALL SQL:
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // You declare WHAT you want (intent):
    List<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);
}
// Spring Data JPA generates the HOW (SQL) automatically:
// SELECT * FROM accounts WHERE user_id = ?   ← generated, hidden

// Business logic knows NOTHING about SQL:
@Service
public class AccountService {
    private final AccountRepository accountRepository; // just the interface

    public List<Account> getUserAccounts(UUID userId) {
        return accountRepository.findByUserId(userId); // reads like English
        // No SQL, no JDBC, no ResultSet — all abstracted away
    }
}

// ABSTRACTION via abstract class — OncePerRequestFilter hides request lifecycle:
public abstract class OncePerRequestFilter implements Filter {
    // Spring calls doFilter() — handles servlet lifecycle details
    @Override
    public final void doFilter(ServletRequest request, ServletResponse response,
                               FilterChain chain) {
        // Handles isAsyncDispatch, isAsyncStarted, etc. — framework details
        doFilterInternal(...); // calls our abstract method
    }

    // WE only implement the abstract method — the interesting part:
    protected abstract void doFilterInternal(HttpServletRequest request,
                                              HttpServletResponse response,
                                              FilterChain filterChain);
}

// Our filter extends the abstract class — focuses ONLY on JWT logic:
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        // ONLY JWT validation logic — no servlet lifecycle complexity
        String token = extractJwt(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            setupSecurityContext(token);
        }
        chain.doFilter(request, response);
    }
}
```

**Result:**
- Business logic reads like domain language: `findActiveAccountsByUserId()` instead of SQL
- Swapping PostgreSQL for another database only requires changing the Repository implementation — zero changes to service layer
- `JwtAuthenticationFilter` focuses purely on JWT logic — Spring handles all the servlet lifecycle details

---

## 6. Interfaces

### STAR Answer

**Situation:**
Our project has multiple "contracts" that need multiple implementations: repositories (PostgreSQL, MongoDB, Cassandra), HTTP clients (real Feign, fallback, mock), event producers (Kafka producer, mock for tests), and security filters.

**Task:**
Define clear contracts (interfaces) that decouple the "what" from the "how" — allowing easy testing, swapping implementations, and extending behaviour.

**Action:**
```java
// 1. Repository interfaces — data access contract:
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // JpaRepository interface provides: save(), findById(), findAll(), delete()...
    // We ADD our custom methods to the contract:
    List<Account> findByUserId(UUID userId);
    boolean existsByAccountNumber(String accountNumber);
}
// Spring Data provides the implementation — we never write it

// 2. Feign client interface — HTTP client contract:
@FeignClient(name = "account-service", fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {
    @GetMapping("/api/v1/accounts/{accountId}/balance")
    BigDecimal getBalance(@PathVariable UUID accountId);
}
// Feign provides the HTTP implementation — we never write it
// Fallback class provides the circuit-breaker implementation

// 3. Multiple interface implementation — SecurityConfig:
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())        // Customizer interface (lambda)
            .sessionManagement(session ->        // Customizer interface (lambda)
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth  // Customizer interface (lambda)
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
// Each lambda implements the Customizer<T> @FunctionalInterface

// 4. Custom interface — for testability:
public interface AuditEventPublisher {
    void publishAuditEvent(String userId, String action, String resource, String status);
}

@Component
public class KafkaAuditEventPublisher implements AuditEventPublisher {
    public void publishAuditEvent(...) {
        eventProducer.publishEvent(BankingConstants.TOPIC_AUDIT_EVENTS, ...);
    }
}

// In tests:
@Component
@Profile("test")
public class NoOpAuditEventPublisher implements AuditEventPublisher {
    public void publishAuditEvent(...) {
        // Do nothing in tests — no real Kafka needed
    }
}
```

**Result:**
- `AccountRepository` interface: change from PostgreSQL to another DB → only change the JPA configuration, zero service layer changes
- `AccountServiceClient` interface: inject mock in tests, Feign in production, fallback in failures — `TransactionService` code never changes
- Spring auto-selects the right implementation at runtime based on profile, availability, and configuration

---

## 7. Enums

### STAR Answer

**Situation:**
In our banking app, account types, statuses, payment rails, loan types, card types, KYC status, fraud risk levels — all have a fixed set of valid values. Using raw Strings like `"CHECKING"` would allow bugs like `"checking"` (wrong case), `"CHEKING"` (typo), or `"DEBIT_CARD"` (wrong category) to slip through.

**Task:**
Ensure that only valid, predefined values are used for categorical data — enforced at compile time, not runtime.

**Action:**
```java
// In Account entity — AccountType enum:
public enum AccountType {
    CHECKING,
    SAVINGS,
    INVESTMENT
    // Cannot pass "CHEQUING" or "savings" — compiler error
}

// In Payment entity — full enum with business logic:
public enum PaymentRail {
    SWIFT,
    FEDWIRE,
    CHIPS,
    INTERNAL,
    ACH;

    // Enums can have methods:
    public boolean isInstant() {
        return this == INTERNAL;
    }

    public boolean requiresBankCode() {
        return this == SWIFT || this == FEDWIRE || this == CHIPS;
    }
}

// In FraudScore model — with score thresholds:
public enum FraudRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean requiresBlock() {
        return this == CRITICAL;
    }

    public boolean requiresAlert() {
        return this == HIGH || this == CRITICAL;
    }
}

// In Loan entity — with financial constants:
public enum LoanType {
    PERSONAL,
    HOME,
    AUTO,
    BUSINESS,
    EDUCATION,
    CREDIT_LINE
}

// Usage — switch on enum (compiler ensures all cases handled):
BigDecimal feePercent = switch (payment.getPaymentRail()) {
    case SWIFT    -> new BigDecimal("0.002");
    case FEDWIRE  -> new BigDecimal("0.001");
    case ACH      -> new BigDecimal("0.0005");
    case CHIPS    -> new BigDecimal("0.001");
    case INTERNAL -> BigDecimal.ZERO;
    // Compiler ERROR if you add a new rail and forget this switch → caught at compile time
};

// Storing in DB — as String (not ordinal):
@Enumerated(EnumType.STRING)   // stores "CHECKING" not 0
private AccountType accountType;
// If you store as ordinal (EnumType.ORDINAL):
// CHECKING=0, SAVINGS=1, INVESTMENT=2
// Later: insert MONEY_MARKET between SAVINGS and INVESTMENT
// → INVESTMENT becomes 3 but all existing DB rows say 2 → data corruption!
// EnumType.STRING prevents this — always stores the name

// Enum in API:
@RequestParam Account.AccountType type
// Spring automatically converts "CHECKING" string from URL → AccountType.CHECKING
// Invalid value like "CHEQUING" → 400 Bad Request automatically
```

**Result:**
- Compile-time safety: typos in account types cause build failure, not runtime bugs
- Switch expressions on enums: the compiler warns if a new enum value is added but not handled in the switch — impossible to forget a case
- `EnumType.STRING` protects against data corruption when enum values are reordered or new ones inserted
