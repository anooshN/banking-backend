# Module 2 — Object-Oriented Programming (OOP)

---

## 1. Classes and Objects

**Situation:**
We need to model real banking entities — accounts, transactions, users, cards, loans. Each entity has data (fields) and behavior (methods).

**Task:**
Create well-structured classes that represent banking concepts, with clear separation between data (entities), business logic (services), and communication (DTOs).

**Action:**

```java
// Three types of classes in our project:

// 1. ENTITY — maps to a database table, has an ID
// services/account-service — Account.java
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;              // identity
    private UUID userId;          // which user owns this
    private AccountType accountType;
    private BigDecimal balance;   // state
    private AccountStatus status;

    // Behavior through enums inside the class
    public enum AccountType { CHECKING, SAVINGS, INVESTMENT }
    public enum AccountStatus { ACTIVE, INACTIVE, FROZEN, CLOSED }
}

// 2. DTO (Data Transfer Object) — carries data between layers, no database mapping
// services/auth-service — LoginRequest.java
@Data
public class LoginRequest {
    // Only has data, no business logic
    private String email;
    private String password;
    private String mfaCode;
    // No @Entity, no @Id — never saved to database directly
}

// 3. SERVICE — contains business logic, stateless (no instance variables storing state)
// services/auth-service — AuthService.java
@Service
@RequiredArgsConstructor
public class AuthService {
    // Dependencies injected (not created here)
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Methods = behavior
    public AuthResponse login(LoginRequest request) { ... }
    public AuthResponse register(RegisterRequest request) { ... }
    public void logout(String token, String userId) { ... }
}
```

**Result:**
- Clear separation: entities know nothing about HTTP, DTOs know nothing about the database, services contain all the logic — each class has one responsibility

**Interview Questions:**
- Q: What is the difference between a class and an object?
  A: "In our banking project, `Account` is a class — it's a blueprint describing what an account looks like: it has a balance, an account type, a status. When a customer opens a new account, we create an object: `Account account = Account.builder().userId(userId).balance(BigDecimal.ZERO).build()`. That specific object with specific values is an instance of the Account class. We can have thousands of Account objects at runtime, all from the same Account class blueprint."

---

## 2. Encapsulation

**Situation:**
Sensitive data like password hashes, JWT secrets, and card numbers must not be directly accessible. Business rules (like "balance cannot go negative") must be enforced consistently.

**Task:**
Hide implementation details and protect data through access modifiers and controlled access.

**Action:**

```java
// services/auth-service — User.java
@Entity
public class User {
    @Id
    private UUID id;

    private String email;

    private String password;        // PRIVATE — never expose the hash directly
    // No getPassword() in the controller — only passwordEncoder.matches() sees it

    private int failedLoginAttempts; // PRIVATE — only AuthService controls this
    private LocalDateTime lockedUntil;

    // The getter exists but updating is controlled through service methods:
    // User.setFailedLoginAttempts() is package-private in production best practice
}

// services/auth-service — AuthService.java
// Encapsulation of lockout LOGIC — enforced in one place
private void handleFailedLogin(User user) {
    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
    if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
        user.setStatus(User.UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
    }
    userRepository.save(user);
}
// No other code can lock an account — ONLY through this method
// This is encapsulation of business rules

// shared/security-lib — JwtTokenProvider.java
@Component
public class JwtTokenProvider {
    @Value("${banking.jwt.secret}")
    private String jwtSecret;  // PRIVATE — no other class can see the secret

    // PRIVATE method — only used internally
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // PUBLIC method — controlled interface for token generation
    public String generateAccessToken(String userId, String email, List<String> roles) {
        return Jwts.builder()
                .setSubject(userId)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // uses private method
                .compact();
    }
}
```

**Result:**
- The JWT secret is never exposed — `private String jwtSecret` with `private getSigningKey()` ensures only `JwtTokenProvider` can create valid tokens
- Account lockout is always applied consistently — no other code can change `failedLoginAttempts` without going through `handleFailedLogin()`

**Interview Questions:**
- Q: What is encapsulation and why is it important?
  A: "In our auth service, we face a security situation: the JWT signing secret must never be exposed. The task was to ensure only the token provider can create tokens. We made `jwtSecret` private and `getSigningKey()` private — no external class can access them. The result: even if another service is compromised, it cannot forge JWT tokens because it cannot access the signing key. This is encapsulation — hiding implementation details behind a controlled interface."

---

## 3. Inheritance

**Situation:**
Different exception types (resource not found, insufficient funds, account locked) all need HTTP status codes and error codes. Writing this logic in every exception class would violate DRY principle.

**Task:**
Create a hierarchy where common behavior lives in the parent class and specific behavior lives in child classes.

**Action:**

```java
// shared/common-utils — Exception Hierarchy

// PARENT — base banking exception with all common fields
public class BankingException extends RuntimeException {
    private final String errorCode;      // "INSUFFICIENT_FUNDS", "ACCOUNT_LOCKED"
    private final HttpStatus httpStatus; // 400, 401, 403, 404, 422, 503

    public BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);  // calls RuntimeException constructor — sets the message
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Overloaded constructor for wrapping another exception (exception chaining)
    public BankingException(String message, String errorCode,
                             HttpStatus httpStatus, Throwable cause) {
        super(message, cause);  // RuntimeException(message, cause)
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

// CHILD 1 — specific to "not found" scenario
public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String resource, String id) {
        super(
            String.format("%s not found with id: %s", resource, id),
            "RESOURCE_NOT_FOUND",   // specific error code
            HttpStatus.NOT_FOUND    // specific HTTP status (404)
        );
        // inherits: getErrorCode(), getHttpStatus(), getMessage() from parent
    }
}

// CHILD 2 — specific to "no money" scenario
public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId) {
        super(
            String.format("Insufficient funds in account: %s", accountId),
            "INSUFFICIENT_FUNDS",
            HttpStatus.UNPROCESSABLE_ENTITY  // 422
        );
    }
}

// USAGE in TransactionService:
if (currentBalance.compareTo(amount) < 0) {
    throw new InsufficientFundsException(accountId.toString());
    // GlobalExceptionHandler catches BankingException (parent)
    // Works for all child exceptions — polymorphism
}

// USAGE in AccountService:
Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
```

**Result:**
- `GlobalExceptionHandler` handles `BankingException` and automatically handles all child exceptions
- Adding a new exception type (`CardExpiredException`) requires only extending `BankingException` — no changes to the handler
- Every exception automatically has HTTP status and error code — consistent API error responses

**Interview Questions:**
- Q: What is inheritance? Give an example from your project.
  A: "In our banking project, we have a situation where we need consistent error responses across 14 microservices. The task was to avoid duplicating HTTP status and error code logic in every exception class. We created `BankingException extends RuntimeException` with `errorCode` and `httpStatus` fields. `ResourceNotFoundException` and `InsufficientFundsException` extend it, each calling `super()` with their specific values. The result: `GlobalExceptionHandler` handles `BankingException` and catches all child exceptions through polymorphism — one handler for the entire exception hierarchy."

- Q: What is method overriding vs overloading?
  A: "In our `BankingException`, we have two constructors — this is overloading: same constructor name, different parameters. One takes message + errorCode + httpStatus. The other adds a `Throwable cause` for exception chaining when wrapping lower-level exceptions. Overriding is different — it's replacing a parent class method in a child class. We override `doFilterInternal()` from `OncePerRequestFilter` in our `JwtAuthenticationFilter` and `CorrelationIdFilter`. The parent defines the structure, we provide the specific implementation."

---

## 4. Polymorphism

**Situation:**
The transaction service calls the account service to check balances. When account-service is down, we need different behavior (fallback) without changing the calling code.

**Task:**
Write calling code against an interface so the actual implementation can be swapped without changing the caller.

**Action:**

```java
// services/transaction-service
// INTERFACE — the contract (what methods exist)
@FeignClient(name = "account-service", fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {
    BigDecimal getBalance(@PathVariable UUID accountId);
    void updateBalance(@PathVariable UUID accountId, @RequestParam BigDecimal amount);
}

// IMPLEMENTATION 1 — normal operation (Feign generates this from the interface)
// Makes HTTP GET to account-service:8083/api/v1/accounts/{id}/balance

// IMPLEMENTATION 2 — when account-service is DOWN
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override  // same method signature as interface — polymorphism
    public BigDecimal getBalance(UUID accountId) {
        throw new BankingException(
            "Account service unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Override
    public void updateBalance(UUID accountId, BigDecimal amount) {
        throw new BankingException("Account service unavailable", ...);
    }
}

// CALLING CODE — doesn't know which implementation it's getting:
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final AccountServiceClient accountServiceClient;
    // Spring injects either the real Feign client or the fallback
    // TransactionService doesn't know or care which one

    public Transaction debit(UUID accountId, BigDecimal amount, ...) {
        BigDecimal balance = accountServiceClient.getBalance(accountId);
        // Same method call works for both real and fallback implementations
    }
}
```

```java
// RUNTIME POLYMORPHISM — method overriding
// shared/security-lib — JwtAuthenticationFilter.java

// Parent class defines the TEMPLATE:
public abstract class OncePerRequestFilter extends GenericFilterBean {
    // Template method pattern — defines when to call our method:
    public final void doFilter(ServletRequest request, ...) {
        // ... setup code ...
        doFilterInternal(httpRequest, httpResponse, filterChain); // calls our override
    }

    // Abstract: MUST be overridden by subclasses
    protected abstract void doFilterInternal(HttpServletRequest request,
                                              HttpServletResponse response,
                                              FilterChain chain);
}

// Our OVERRIDE — specific behavior for JWT:
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        // Our specific JWT validation logic
        String jwt = extractJwtFromRequest(request);
        if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
            // set authentication...
        }
        filterChain.doFilter(request, response);
    }
}
// Spring calls doFilter() on the parent → parent calls our doFilterInternal()
// The caller uses the parent type, our code runs — polymorphism
```

**Result:**
- When account-service fails, Resilience4j switches to `AccountServiceClientFallback` transparently — `TransactionService` code doesn't change
- Spring Security calls `doFilterInternal()` through the parent type — our JWT filter runs without Spring needing to know the specific class

**Interview Questions:**
- Q: What is the difference between compile-time and runtime polymorphism?
  A: "Compile-time polymorphism is method overloading — resolved at compile time based on parameter types. In our `BankingException`, we have two constructors with different parameters. Runtime polymorphism is method overriding — resolved at runtime based on the actual object type. In our Feign client setup, `AccountServiceClient` is the interface type. At runtime, Spring injects either the real HTTP client or the fallback. `TransactionService` calls `accountServiceClient.getBalance()` — the same line of code, but different behavior depending on which implementation is injected. This is runtime polymorphism."

---

## 5. Abstraction

**Situation:**
Services need to access data without knowing the database implementation details. A controller should call a service method without knowing whether it uses PostgreSQL, Redis, or makes an HTTP call.

**Task:**
Define interfaces that describe WHAT to do without specifying HOW — letting implementations vary independently.

**Action:**

```java
// ABSTRACT INTERFACE — says what operations exist, not how they work:
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // JpaRepository<Account, UUID> declares:
    //   save(Account), findById(UUID), findAll(), deleteById(UUID), etc.
    // Our additions:
    List<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    // These method signatures describe WHAT — Spring Data generates the HOW
}

// Caller (AccountService) uses the interface, not the implementation:
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    // Spring injects a dynamically-generated implementation
    // AccountService doesn't know if it's PostgreSQL, H2 (test), or anything else

    public Account getAccountById(UUID id) {
        return accountRepository.findById(id).orElseThrow(...);
        // Calls interface method — works regardless of underlying database
    }
}

// In tests, we can inject a MOCK:
// @Mock AccountRepository accountRepository;
// when(accountRepository.findById(id)).thenReturn(Optional.of(testAccount));
// The same AccountService code works with the mock
```

```java
// ABSTRACT CLASS — partial implementation with hooks for subclasses
// services/api-gateway — AuthenticationFilter.java

public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {
    // AbstractGatewayFilterFactory provides:
    // - shortcutFieldOrder() method
    // - name() method (returns class name by default)
    // - newConfig() method
    // WE provide:
    @Override
    public GatewayFilter apply(Config config) {
        // Our specific JWT validation logic
        return (exchange, chain) -> { ... };
    }
    // AbstractGatewayFilterFactory handles registration with Spring Cloud Gateway
    // We only implement what's unique to our filter
}
```

**Result:**
- Switching from PostgreSQL to a different database requires changing only the repository implementation — `AccountService` code stays identical
- Tests can use mock repositories — fast, no real database needed

**Interview Questions:**
- Q: What is the difference between abstract class and interface?
  A: "In our project, we use both. `AccountRepository` is an interface — it's a pure contract, all methods are abstract, Spring Data generates the implementation. `AbstractGatewayFilterFactory` is an abstract class — it provides partial implementation (registration, configuration) and leaves `apply()` as abstract for us to implement. Use interface when you need a pure contract with multiple implementations. Use abstract class when you have shared code plus customization points."

---

## 6. Enums

**Situation:**
Throughout the banking app, fields have a fixed set of valid values — account types, payment rails, loan types, fraud risk levels. Using plain Strings would allow invalid values.

**Task:**
Use enums to restrict field values to a valid set, make code readable, and enable compile-time checking.

**Action:**

```java
// services/account-service — Account.java
@Enumerated(EnumType.STRING)  // store "CHECKING" not 0 in database
public enum AccountType { CHECKING, SAVINGS, INVESTMENT }
// Instead of: private String accountType = "CHECKING"
// With enum: private AccountType accountType = AccountType.CHECKING
// Invalid: accountType = AccountType.INVALID — compile error
// Invalid: accountType = "CHEKKING" — compile error (no such enum constant)

// services/payment-service — Payment.java
public enum PaymentRail { SWIFT, FEDWIRE, CHIPS, INTERNAL, ACH }
// In switch expression:
if (payment.getPaymentRail() == Payment.PaymentRail.SWIFT) {
    payment.setSwiftMessage(buildSwiftMT103(payment));
}
// Compiler knows all possible values — no default case needed

// services/loan-service — Loan.java
public enum LoanStatus {
    APPLIED, UNDER_REVIEW, APPROVED, ACTIVE, CLOSED, DEFAULTED, REJECTED
}
// State machine: a loan moves through these states in order
// Can only be one status at a time

// services/fraud-detection-service — FraudScore.java
public enum FraudRisk { LOW, MEDIUM, HIGH, CRITICAL }
// Fraud risk is always one of exactly these four values
// The score → risk mapping is always consistent:
FraudScore.FraudRisk risk = score < 0.3 ? FraudScore.FraudRisk.LOW
        : score < 0.5 ? FraudScore.FraudRisk.MEDIUM
        : score < 0.8 ? FraudScore.FraudRisk.HIGH
        : FraudScore.FraudRisk.CRITICAL;
```

```java
// ENUM with behavior — enums can have fields and methods:
// Extending the PaymentRail enum with routing logic:
public enum PaymentRail {
    SWIFT("international", 1_000_000_00),
    FEDWIRE("domestic-large", 10_000_000_00),
    CHIPS("domestic-large", 10_000_000_00),
    INTERNAL("internal", Integer.MAX_VALUE),
    ACH("domestic-small", 25_000_00);

    private final String category;
    private final long maxAmountCents;

    PaymentRail(String category, long maxAmountCents) {
        this.category = category;
        this.maxAmountCents = maxAmountCents;
    }

    public boolean isValidAmount(long amountCents) {
        return amountCents <= maxAmountCents;
    }
}
// Now: PaymentRail.SWIFT.isValidAmount(5_000_00) → true
// This keeps rail-specific rules in the enum — not scattered in if-else
```

**Result:**
- `@Enumerated(EnumType.STRING)` stores "CHECKING" instead of 0 — adding a new AccountType doesn't corrupt existing data (if ordinal changes, all stored integers become wrong)
- Compile-time checking prevents typos like "CHEKKING" or "SAVINGSS" that would only fail at runtime with strings

**Interview Questions:**
- Q: Why use EnumType.STRING instead of EnumType.ORDINAL?
  A: "In our account service, we store AccountType as an enum. If we used ORDINAL, CHECKING=0, SAVINGS=1, INVESTMENT=2. If we later add MONEY_MARKET between SAVINGS and INVESTMENT, INVESTMENT becomes ordinal 3. All existing database rows with value 2 now map to MONEY_MARKET — corrupted data. With STRING, we store 'CHECKING', 'SAVINGS', 'INVESTMENT'. Adding MONEY_MARKET doesn't change existing rows. Always use EnumType.STRING in production systems."

- Q: Can enums have methods and fields in Java?
  A: "Yes. In our project, we could extend PaymentRail enum to have a maxAmount field and an isValidAmount() method. Each enum constant can have different values for these fields. This keeps rail-specific business rules in one place — the enum itself — instead of scattered across if-else chains in services."
