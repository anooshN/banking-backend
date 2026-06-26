# Spring Boot

## What Is Spring Boot?

**Simple explanation:** Spring Boot is a framework that gets a Java web server running with minimal configuration. Without it, you'd spend days configuring XML files just to start. With it, you write `@SpringBootApplication`, run the `main()` method, and have a running web server.

Spring Boot wraps the older Spring Framework and adds:
- **Auto-configuration:** Detects what's on your classpath and configures it automatically
- **Embedded server:** Tomcat runs inside your JAR — no separate server installation
- **Opinionated defaults:** Sensible defaults you can override when needed
- **Actuator:** Production-ready health checks and metrics out of the box

---

## Entry Point — @SpringBootApplication

Every service has exactly one entry point:

```java
// auth-service/AuthServiceApplication.java
@SpringBootApplication       // 3 annotations in 1:
                             //   @Configuration (this class defines beans)
                             //   @EnableAutoConfiguration (auto-configure based on classpath)
                             //   @ComponentScan (scan this package for @Component classes)
@EnableDiscoveryClient       // register with Eureka
@EnableAsync                 // allow @Async methods to run on background threads
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
        // What this does:
        // 1. Creates Spring ApplicationContext (the IoC container)
        // 2. Scans all @Component, @Service, @Repository, @Controller classes
        // 3. Creates beans and injects dependencies
        // 4. Starts embedded Tomcat on configured port
        // 5. Registers with Eureka
        // 6. Application is ready to serve requests
    }
}
```

---

## Dependency Injection (IoC Container)

**Simple explanation:** Instead of creating objects yourself (`new AccountService()`), you declare what you need and Spring creates and injects it for you. Spring manages the lifecycle.

**How it works:**

```java
// 1. Mark a class as a Spring-managed bean:
@Service  // tells Spring: create one instance of this and manage it
public class AccountService {
    private final AccountRepository accountRepository;
    private final BankingEventProducer eventProducer;

    // 2. Constructor injection (recommended over field injection)
    // Spring sees this constructor and automatically provides the parameters
    @RequiredArgsConstructor  // Lombok generates this constructor
    // equivalent to:
    // public AccountService(AccountRepository accountRepository,
    //                       BankingEventProducer eventProducer) {
    //     this.accountRepository = accountRepository;
    //     this.eventProducer = eventProducer;
    // }
}

// 3. Use the service in a controller:
@RestController
@RequiredArgsConstructor  // Spring injects AccountService automatically
public class AccountController {
    private final AccountService accountService;
    // Spring finds the AccountService bean created in step 1
    // and puts it here — no 'new' required
}
```

**Why constructor injection over field injection?**
```java
// BAD — field injection (many teams use this but it's worse)
@Autowired
private AccountService accountService;
// Problems:
// - Can't make it final (immutable)
// - Hard to test (can't pass mock in constructor)
// - Hidden dependency (class doesn't declare what it needs)

// GOOD — constructor injection (what we use)
private final AccountService accountService;
// Benefits:
// - final = immutable, thread-safe
// - Easy to test: new AccountController(mockAccountService)
// - Explicit dependency declaration
```

---

## @RestController and Request Mapping

```java
@RestController                    // = @Controller + @ResponseBody
                                   // @Controller: marks this as a web controller
                                   // @ResponseBody: return values are written directly
                                   //   to the HTTP response body as JSON
@RequestMapping("/api/v1/accounts") // base URL prefix for all methods in this class
@RequiredArgsConstructor
@Tag(name = "Accounts")            // Swagger/OpenAPI tag
@SecurityRequirement(name = "bearerAuth") // Swagger: show lock icon
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountId}")          // handles GET /api/v1/accounts/{accountId}
    @PreAuthorize("hasRole('CUSTOMER')") // Spring Security checks this before method runs
    public ResponseEntity<ApiResponse<Account>> getAccount(
            @PathVariable UUID accountId) {  // extracts {accountId} from URL path
        
        Account account = accountService.getAccountById(accountId);
        
        // ResponseEntity gives control over HTTP status code + body
        return ResponseEntity.ok(           // 200 OK
                ApiResponse.success(account) // our standard wrapper
        );
    }

    @PostMapping("/user/{userId}")         // handles POST /api/v1/accounts/user/{userId}
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Account>> createAccount(
            @PathVariable UUID userId,
            @RequestParam Account.AccountType type,      // ?type=CHECKING in URL
            @RequestParam(defaultValue = "USD") String currency) {

        Account account = accountService.createAccount(userId, type, currency);
        return ResponseEntity.ok(ApiResponse.success(account, "Account created"));
    }

    @PatchMapping("/{accountId}/freeze")   // PATCH for partial update
    public ResponseEntity<ApiResponse<Account>> freezeAccount(
            @PathVariable UUID accountId,
            @RequestHeader("X-User-Id") String userId) { // read from HTTP header
        
        return ResponseEntity.ok(ApiResponse.success(
                accountService.freezeAccount(accountId)));
    }
}
```

---

## @Service, @Repository, @Component

These are all specializations of `@Component`. Spring creates one instance of each (singleton by default).

```java
@Component    // generic Spring-managed bean
@Service      // = @Component but semantically means "business logic"
@Repository   // = @Component but adds exception translation (SQL exceptions → Spring exceptions)
@Controller   // = @Component but marks web controllers
@RestController // = @Controller + @ResponseBody
```

**Why the distinction?**
1. Code readability — you immediately know what a class does
2. AOP pointcuts — you can target `@Repository` classes specifically
3. `@Repository` specifically adds PersistenceExceptionTranslationPostProcessor

---

## Configuration Properties

Instead of hardcoded values, use properties bound to Java objects:

```java
// application.yml
banking:
  auth:
    max-failed-attempts: 5
    lockout-duration-minutes: 30

// AuthProperties.java
@ConfigurationProperties(prefix = "banking.auth")
@Data
public class AuthProperties {
    private int maxFailedAttempts = 5;        // default value
    private int lockoutDurationMinutes = 30;
}

// Enable it:
@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class AuthServiceApplication { ... }

// Use it:
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthProperties authProperties;

    private void handleFailedLogin(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= authProperties.getMaxFailedAttempts()) {
            user.setLockedUntil(
                LocalDateTime.now().plusMinutes(authProperties.getLockoutDurationMinutes())
            );
        }
    }
}
```

**Why this is better than `@Value("${banking.auth.max-failed-attempts}")`:**
- Grouped in one class instead of scattered fields
- Type-safe (int, not String)
- IDE autocomplete in application.yml
- Easy to validate with Bean Validation annotations

---

## @Transactional — Database Transaction Management

**Simple explanation:** Everything inside a @Transactional method either ALL succeeds or ALL fails. No partial updates.

```java
@Service
public class AuthService {

    @Transactional  // wraps the entire method in a database transaction
    public AuthResponse register(RegisterRequest request) {
        // Step 1: check email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BankingException("Email exists", "EMAIL_EXISTS", HttpStatus.CONFLICT);
            // If we throw here, nothing was saved — transaction rolls back
        }

        // Step 2: create user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user); // INSERT INTO users ...

        // Step 3: publish Kafka event
        eventProducer.publishEvent("banking.user.events", user.getId().toString(), "USER_REGISTERED");

        // If any exception happens anywhere above, ALL database changes are rolled back
        // The user is NOT saved, even if step 2 succeeded
        return generateTokens(user);
    }
    // At the end of the method: COMMIT — changes are permanently saved
}
```

**Propagation levels (how nested transactions work):**
```java
@Transactional(propagation = Propagation.REQUIRED)  // default
// If a transaction exists: join it. If not: create one.
// Most methods use this.

@Transactional(propagation = Propagation.REQUIRES_NEW)
// Always create a new transaction, suspend the outer one.
// Used for audit logging — audit should always save, even if outer tx fails.

@Transactional(readOnly = true)
// Hint to database: this is a read-only query, no locking needed.
// Slightly faster for queries.
```

---

## Profiles (@Profile, spring.profiles.active)

**What it is:** Different configuration for different environments (dev, test, prod).

```java
// Only create this bean in test profile
@Bean
@Profile("test")
public MockEmailService emailService() {
    return new MockEmailService(); // doesn't actually send emails
}

// Only in production
@Bean
@Profile("prod")
public RealEmailService emailService() {
    return new RealEmailService(smtpConfig); // sends real emails
}
```

**In application.yml:**
```yaml
# application.yml (always loaded)
server:
  port: 8081

# application-dev.yml (loaded when profile = dev)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/banking_auth

# application-prod.yml (loaded when profile = prod)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/banking_auth
```

**Activation:**
```bash
# Via environment variable (used in Docker/Kubernetes)
SPRING_PROFILES_ACTIVE=prod java -jar auth-service.jar

# Via command line
java -jar auth-service.jar --spring.profiles.active=dev
```

---

## @Scheduled — Cron Jobs

**Used in payment-service (Outbox pattern) and report-service (monthly statements):**

```java
@Service
public class PaymentService {

    // Runs every 5000 milliseconds (5 seconds)
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();
        for (Payment payment : unprocessed) {
            eventProducer.publishEvent(
                "banking.payment.events",
                payment.getId().toString(),
                "PAYMENT_INITIATED:" + payment.getPaymentReference()
            );
            payment.setOutboxProcessed(true);
            paymentRepository.save(payment);
        }
    }
}

@Service
public class StatementService {

    // Cron expression: second minute hour day month weekday
    @Scheduled(cron = "0 0 1 1 * *")  // 01:00 on the 1st of every month
    public void generateMonthlyStatements() {
        log.info("Starting monthly statement batch job");
        // fetch all active accounts and kick off batch jobs
    }
}

// Must enable on Application class:
@SpringBootApplication
@EnableScheduling  // required for @Scheduled to work
public class PaymentServiceApplication { ... }
```

---

## @Async — Background Tasks

**Used in notification-service email sending:**

```java
@Service
public class EmailService {

    // This method runs on a background thread, not the HTTP request thread
    @Async
    public void sendEmail(String to, String subject, String body) {
        // This could take 2-3 seconds
        // Without @Async: the HTTP request waits 2-3 seconds
        // With @Async: the HTTP request returns immediately, email sends in background
        mailSender.send(buildMessage(to, subject, body));
    }
}

// Enable on Application class:
@SpringBootApplication
@EnableAsync
public class NotificationServiceApplication { ... }
```

---

## @Cacheable, @CacheEvict — Caching with Redis

**Used in account-service:**

```java
@Service
public class AccountService {

    // When this method is called:
    // 1. Check Redis for key "account::{accountId}"
    // 2. If found: return cached value (never runs method body)
    // 3. If not found: run method, store result in Redis, return result
    @Cacheable(value = "account", key = "#accountId")
    public Account getAccountById(UUID accountId) {
        // This database query is skipped if Redis has the result
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
    }

    // When this method runs: delete key "account::{accountId}" from Redis
    // So the next call to getAccountById goes to the database (fresh data)
    @CacheEvict(value = "account", key = "#accountId")
    @Transactional
    public Account updateBalance(UUID accountId, BigDecimal amount) {
        Account account = getAccountById(accountId);
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    // Evict ALL entries in the "account" cache
    @CacheEvict(value = "account", allEntries = true)
    public void clearAllAccountCache() { }
}

// Configure Redis as the cache backend:
// application.yml
// spring.cache.type=redis
// spring.cache.redis.time-to-live=300000  (5 minutes in milliseconds)
```

---

## Exception Handling — @RestControllerAdvice

**One place to handle all exceptions from all controllers:**

```java
@Slf4j
@RestControllerAdvice  // applies to ALL @RestController classes in the application
public class GlobalExceptionHandler {

    // Handles our custom BankingException
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
        log.error("Banking exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())   // use the status from the exception
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    // Handles @Valid validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        // Collect all field errors into a map
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .errorCode("VALIDATION_ERROR")
                        .data(errors)
                        .build());
    }

    // Catches ANYTHING not caught above — last resort
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);  // log with full stack trace
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_SERVER_ERROR"));
    }
}
```

**Without @RestControllerAdvice:**
Every controller method would need try-catch blocks. With it, you write exception handling once and it applies everywhere.
