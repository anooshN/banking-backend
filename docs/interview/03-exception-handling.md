# Module 03 — Exception Handling

---

## 1. Custom Exception Hierarchy

### STAR Answer

**Situation:**
Our banking application has 14 microservices. Every service can fail in domain-specific ways — account not found, insufficient funds, account locked, invalid JWT, service unavailable. Without a structured exception hierarchy, every service would have its own inconsistent error format and HTTP status codes.

**Task:**
Design a custom exception hierarchy that carries the HTTP status code and a machine-readable error code, so every service returns consistent, structured error responses — automatically.

**Action:**
```java
// ── BASE CLASS ───────────────────────────────────────────────────────────────
// In shared/common-utils — BankingException.java:
public class BankingException extends RuntimeException {
    // RuntimeException = unchecked — callers don't have to declare throws
    // This is intentional: we don't want to force every method to declare
    // throws BankingException for domain errors

    private final String errorCode;       // machine-readable: "INSUFFICIENT_FUNDS"
    private final HttpStatus httpStatus;  // HTTP: 404, 422, 503, etc.

    public BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);  // calls RuntimeException(String message)
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Overloaded — when wrapping a lower-level exception:
    public BankingException(String message, String errorCode,
                             HttpStatus httpStatus, Throwable cause) {
        super(message, cause);  // preserves the original stack trace
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Lombok @Getter generates these:
    public String getErrorCode()        { return errorCode; }
    public HttpStatus getHttpStatus()   { return httpStatus; }
}

// ── SUBCLASS 1: Resource Not Found ───────────────────────────────────────────
public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String resource, String id) {
        super(
            String.format("%s not found with id: %s", resource, id),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND     // 404
        );
        // Calls BankingException constructor — inherits all its behaviour
    }
}

// ── SUBCLASS 2: Insufficient Funds ───────────────────────────────────────────
public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId) {
        super(
            String.format("Insufficient funds in account: %s", accountId),
            "INSUFFICIENT_FUNDS",
            HttpStatus.UNPROCESSABLE_ENTITY  // 422: valid request, can't process
        );
    }
}

// ── USAGE ─────────────────────────────────────────────────────────────────────
// In AccountService:
public Account getAccountById(UUID accountId) {
    return accountRepository.findById(accountId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Account", accountId.toString())
            );
    // → 404 {"errorCode":"RESOURCE_NOT_FOUND","message":"Account not found with id: ..."}
}

// In TransactionService:
if (currentBalance.compareTo(amount) < 0) {
    throw new InsufficientFundsException(accountId.toString());
    // → 422 {"errorCode":"INSUFFICIENT_FUNDS","message":"Insufficient funds in account: ..."}
}

// In AuthService:
if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
    throw new BankingException("Invalid credentials", "INVALID_CREDENTIALS",
            HttpStatus.UNAUTHORIZED);  // 401
}
```

**Result:**
- Every exception automatically carries its HTTP status — no manual `ResponseEntity.status()` in every controller
- Machine-readable error codes like `"INSUFFICIENT_FUNDS"` let the frontend show the right message to users
- Adding a new exception type (e.g., `DailyLimitExceededException`) = 5 lines, no changes to existing code

---

## 2. Global Exception Handler — @RestControllerAdvice

### STAR Answer

**Situation:**
Without centralised exception handling, every controller method would need try-catch blocks. With 14 services and dozens of endpoints, that's hundreds of duplicate error-handling blocks. If the error format changes, you'd update hundreds of places.

**Task:**
Create ONE class that catches ALL exceptions from ALL controllers across the service, formats them consistently, and returns the right HTTP status.

**Action:**
```java
// In shared/exception-lib — GlobalExceptionHandler.java:
@Slf4j
@RestControllerAdvice   // applies to ALL @RestController classes in the application
                        // intercepts exceptions before they reach the HTTP layer
public class GlobalExceptionHandler {

    // ── Handles OUR custom exceptions ────────────────────────────────────────
    @ExceptionHandler(BankingException.class)
    // Catches BankingException AND all subclasses
    // (ResourceNotFoundException, InsufficientFundsException, etc.)
    public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
        log.error("Banking exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())  // uses the status stored in the exception
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    // ── Handles @Valid validation failures ────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        // Collect all field-level errors into a map:
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message   = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });
        // Response: {"email":"must be a valid email","password":"must be at least 8 chars"}

        return ResponseEntity.badRequest()  // 400
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .errorCode("VALIDATION_ERROR")
                        .data(fieldErrors)
                        .build());
    }

    // ── Handles Spring Security: access denied ────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)  // 403
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    // ── Handles Spring Security: not authenticated ────────────────────────────
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)  // 401
                .body(ApiResponse.error("Authentication required", "UNAUTHORIZED"));
    }

    // ── Catches EVERYTHING else — safety net ──────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Log with FULL stack trace (important for debugging unexpected errors)
        log.error("Unexpected error occurred: ", ex);
        // Return generic message — never expose stack trace to the client
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
                .body(ApiResponse.error(
                    "An unexpected error occurred. Please try again.",
                    "INTERNAL_SERVER_ERROR"
                ));
    }
}
```

**Result:**
- Zero try-catch blocks in any controller — all exception handling in one place
- Consistent error response format: `{"success":false,"message":"...","errorCode":"..."}`
- Changing the error response format = change ONE class — all 14 services pick it up (shared library)
- Unexpected exceptions never expose stack traces to clients (security)

---

## 3. try-catch-finally in Kafka Consumer

### STAR Answer

**Situation:**
Our Kafka consumers (notification-service, audit-service, fraud-detection-service) process events from multiple topics. If processing one event fails, we must NOT acknowledge it to Kafka (so it gets redelivered) AND we must NOT let one failure stop the processing of other events.

**Task:**
Handle exceptions in Kafka listeners so: failures are logged, messages are not lost (no premature ack), processing continues for other messages, and audit always happens.

**Action:**
```java
// In NotificationService.java:
@KafkaListener(topics = "banking.transaction.events",
               groupId = "notification-service-group")
public void handleTransactionEvent(String event, Acknowledgment ack) {

    try {
        // ── TRY: attempt to process the event ────────────────────────────────
        log.info("Processing transaction event: {}", event);

        // Parse event and extract user info (would be Avro in production)
        String userId = extractUserId(event);
        String description = formatNotificationMessage(event);

        // Create in-app notification (MongoDB):
        Notification notification = createNotification(
                userId, "Transaction Alert", description,
                Notification.NotificationType.TRANSACTION, null
        );

        // Push to browser via WebSocket:
        messagingTemplate.convertAndSendToUser(
                userId, "/queue/notifications", notification
        );

        // ── ACK: only after SUCCESSFUL processing ─────────────────────────────
        ack.acknowledge();
        // Tells Kafka: "I successfully processed this message, move my offset forward"
        // If we ack here and then crash, the message is gone — so we ack LAST

    } catch (JsonParseException e) {
        // Bad message format — will always fail, no point retrying
        log.error("Invalid event format, sending to DLQ: {}", event, e);
        ack.acknowledge(); // ack anyway — prevent infinite retry of malformed message
        // Spring's error handler will route this to the DLQ after max retries

    } catch (MongoException e) {
        // MongoDB temporarily unavailable — might succeed on retry
        log.error("MongoDB error processing event, will retry: {}", e.getMessage());
        // DON'T call ack.acknowledge()
        // Kafka will redeliver this message to this consumer group
        // DefaultErrorHandler retries 3 times before sending to DLQ

    } catch (Exception e) {
        // Unexpected error — log and let Kafka retry
        log.error("Unexpected error processing transaction event: {}", e.getMessage(), e);
        // DON'T ack — message will be retried
    }
}

// In AuditAspect — finally guarantees audit always happens:
@Around("@annotation(auditable)")
public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable)
        throws Throwable {

    long startTime = System.currentTimeMillis();
    String status = "SUCCESS";
    String errorMessage = null;
    Object result = null;

    try {
        // ── TRY: run the actual business method ──────────────────────────────
        result = joinPoint.proceed(); // e.g., AccountService.updateBalance()
        return result;

    } catch (Throwable ex) {
        // ── CATCH: capture error details for audit ───────────────────────────
        status = "FAILURE";
        errorMessage = ex.getMessage();
        throw ex; // re-throw so the original caller still gets the exception

    } finally {
        // ── FINALLY: ALWAYS runs — success OR failure ─────────────────────────
        long durationMs = System.currentTimeMillis() - startTime;
        try {
            publishAuditEvent(auditable, status, errorMessage, durationMs);
            // If updateBalance succeeded → audit shows SUCCESS
            // If updateBalance threw exception → audit shows FAILURE
            // Either way, the audit record is always created
        } catch (Exception auditEx) {
            // NEVER let audit failure break the business operation
            // The account update succeeded — don't roll it back because audit failed
            log.error("Failed to publish audit event: {}", auditEx.getMessage());
        }
    }
}
```

**Result:**
- Messages are never silently lost: failed processing = no ack = Kafka redelivers
- Audit trail is complete: `finally` guarantees the audit event is published regardless of success or failure
- One bad message doesn't kill the consumer: `catch` logs and decides whether to ack or not, then continues to the next message

---

## 4. Checked vs Unchecked Exceptions

### STAR Answer

**Situation:**
Java has two kinds of exceptions: **checked** (must be declared or caught) and **unchecked** (extend RuntimeException — optional to catch). Our banking app uses both deliberately.

**Task:**
Choose the right exception type for each situation — checked for recoverable external failures, unchecked for programming errors and domain violations.

**Action:**
```java
// ── UNCHECKED (RuntimeException) — our domain exceptions ─────────────────────
// We extend RuntimeException for ALL our custom exceptions:
public class BankingException extends RuntimeException { ... }
public class ResourceNotFoundException extends BankingException { ... }
public class InsufficientFundsException extends BankingException { ... }

// WHY unchecked?
// If we used checked exceptions, EVERY method in the call chain would need:
public Account getAccount(UUID id) throws ResourceNotFoundException { ... }
public void processTransfer(...) throws ResourceNotFoundException,
                                        InsufficientFundsException,
                                        BankingException { ... }
// This propagates through all layers — clutters every method signature
// And Spring's @Transactional only rolls back for RuntimeException by default

// With unchecked exceptions:
public Account getAccount(UUID id) { // clean — no throws declaration
    return repo.findById(id).orElseThrow(() ->
        new ResourceNotFoundException("Account", id.toString())
    );
}
// Spring's @Transactional automatically rolls back on any RuntimeException

// ── CHECKED — for external I/O that can fail unexpectedly ─────────────────────
// JavaMail throws checked exceptions:
// In EmailService.java (using @SneakyThrows from Lombok):
@Async
@SneakyThrows   // wraps checked MessagingException in RuntimeException
public void sendEmail(String to, String subject, String body) {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);
    helper.setTo(to);                    // throws MessagingException (checked)
    helper.setSubject(subject);          // throws MessagingException (checked)
    helper.setText(body, true);          // throws MessagingException (checked)
    mailSender.send(message);
    // Without @SneakyThrows, we'd need:
    // throws MessagingException OR try { } catch (MessagingException e) { throw new RuntimeException(e); }
    // @SneakyThrows does that wrapping automatically
}

// ── MULTI-CATCH — JWT validation handles multiple exception types ──────────────
// In JwtTokenProvider.java:
public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
        return true;
    } catch (ExpiredJwtException e) {
        log.warn("JWT token expired: {}", e.getMessage());
        // Don't log as ERROR — expiry is expected (every 15 min)
    } catch (UnsupportedJwtException | MalformedJwtException e) {
        log.error("Invalid JWT token format: {}", e.getMessage());
    } catch (SignatureException e) {
        log.error("JWT signature invalid — possible forgery attempt: {}", e.getMessage());
        // This is a security event — could alert the security team here
    } catch (IllegalArgumentException e) {
        log.error("JWT token is null or empty: {}", e.getMessage());
    }
    return false;
}
// Multi-catch: UnsupportedJwtException | MalformedJwtException
// Both handled the same way — avoids duplicating the log statement

// ── @Transactional rollback control ──────────────────────────────────────────
@Transactional(
    rollbackFor = Exception.class,        // roll back for ALL exceptions (including checked)
    noRollbackFor = ResourceNotFoundException.class  // but NOT for this one
)
public Account processTransfer(UUID fromId, UUID toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", fromId.toString()));
    // ResourceNotFoundException → does NOT roll back (no point, nothing changed yet)

    if (from.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException(fromId.toString());
    }
    // InsufficientFundsException (RuntimeException) → DOES roll back by default
}
```

**Result:**
- Unchecked exceptions for domain violations: clean method signatures, automatic `@Transactional` rollback
- `@SneakyThrows` where checked exceptions are forced by external APIs (JavaMail): avoid polluting every method signature
- Multi-catch consolidates identical handling of different exception types
- `rollbackFor` / `noRollbackFor` gives fine-grained control over which exceptions trigger database rollback
