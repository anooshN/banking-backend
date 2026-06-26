# Module 3 — Exception Handling

---

## 1. Custom Exception Hierarchy

**Situation:**
14 microservices all throw exceptions. Without structure, every service would format errors differently — one returns `{"error": "not found"}`, another returns `{"message": "Account missing"}`. The frontend can't handle inconsistent formats.

**Task:**
Create a consistent exception hierarchy so all services produce the same error format regardless of which exception is thrown.

**Action:**

```java
// shared/common-utils — BankingException.java (ROOT of hierarchy)
public class BankingException extends RuntimeException {
    // WHY RuntimeException? Unchecked — doesn't force callers to catch it
    // Checked exceptions (extends Exception) force every method signature to declare them
    // In a 14-service app, that's hundreds of unnecessary try-catch blocks

    private final String errorCode;      // machine-readable: "INSUFFICIENT_FUNDS"
    private final HttpStatus httpStatus; // HTTP response code: 422

    // Constructor 1: message + code + status
    public BankingException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);       // sets getMessage() — human readable: "Account has insufficient funds"
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Constructor 2: adds cause — exception chaining
    public BankingException(String message, String errorCode,
                             HttpStatus httpStatus, Throwable cause) {
        super(message, cause);  // preserves original stack trace
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    // Getters only — no setters (immutable exception)
    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}

// shared/common-utils — ResourceNotFoundException.java (CHILD)
public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String resource, String id) {
        super(
            String.format("%s not found with id: %s", resource, id),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND   // 404
        );
    }
}
// Usage: throw new ResourceNotFoundException("Account", accountId.toString());
// Message auto-generated: "Account not found with id: 550e8400-e29b..."

// shared/common-utils — InsufficientFundsException.java (CHILD)
public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId) {
        super(
            String.format("Insufficient funds in account: %s", accountId),
            "INSUFFICIENT_FUNDS",
            HttpStatus.UNPROCESSABLE_ENTITY  // 422 — request is valid but can't be processed
        );
    }
}
```

**Result:**
- All 14 services throw consistent, structured exceptions
- One `GlobalExceptionHandler` handles everything — catches `BankingException` and all its children via polymorphism
- Frontend always receives: `{"success": false, "message": "...", "errorCode": "..."}`

---

## 2. @RestControllerAdvice — Global Exception Handling

**Situation:**
Without centralized exception handling, every controller method needs try-catch blocks. With 50+ endpoints across 14 services, that's 50+ places to handle the same exceptions.

**Task:**
Handle all exceptions in one place, convert them to consistent API responses.

**Action:**

```java
// shared/exception-lib — GlobalExceptionHandler.java
@Slf4j
@RestControllerAdvice  // applies to ALL @RestController classes in the application
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// @ControllerAdvice: intercepts exceptions from all controllers
// @ResponseBody: return values are written to HTTP response as JSON
public class GlobalExceptionHandler {

    // Handles our custom hierarchy — catches BankingException AND all its children
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
        log.error("Banking exception: [{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())              // use the status embedded in exception
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }
    // This ALSO catches ResourceNotFoundException and InsufficientFundsException
    // because they extend BankingException — polymorphism

    // Handles Spring's validation failure (@Valid annotation)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        // Collect ALL field errors — not just the first one
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
            // {"email": "must be a valid email", "password": "must be at least 8 characters"}
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)  // 400
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .errorCode("VALIDATION_ERROR")
                        .data(errors)
                        .build());
    }

    // Handles Spring Security's 403 Forbidden
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)  // 403
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    // Handles Spring Security's 401 Unauthorized
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)  // 401
                .body(ApiResponse.error("Authentication required", "UNAUTHORIZED"));
    }

    // CATCH-ALL — must be last, handles anything not caught above
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);  // log full stack trace
        // NEVER expose internal details to the client — security risk
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_SERVER_ERROR"));
    }
}
```

**Result:**
- Zero try-catch in controllers — clean business logic
- Consistent error format across all 50+ endpoints
- Security: `Exception.class` handler never exposes stack traces to clients

---

## 3. try-catch-finally

**Situation:**
In the AuditAspect, we need to capture whether a method succeeded or failed AND always publish the audit event regardless of the outcome. In Kafka consumers, we must only acknowledge messages we successfully processed.

**Task:**
Use try-catch-finally to guarantee cleanup/reporting even when exceptions occur.

**Action:**

```java
// shared/audit-lib — AuditAspect.java
// CLASSIC try-catch-finally pattern
@Around("@annotation(auditable)")
public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
    String status = "SUCCESS";
    String errorMessage = null;
    long startTime = System.currentTimeMillis();
    Object result = null;

    try {
        // The actual business method runs here
        result = joinPoint.proceed();
        return result;
        // If method succeeds: status remains "SUCCESS", result is set

    } catch (Throwable ex) {
        // Any exception from the method:
        status = "FAILURE";
        errorMessage = ex.getMessage();
        throw ex;  // RE-THROW — don't swallow the exception
        // The caller still gets the exception — we just captured info about it

    } finally {
        // ALWAYS runs — success OR failure OR exception
        long durationMs = System.currentTimeMillis() - startTime;
        publishAuditEvent(auditable, status, errorMessage, durationMs);
        // Audit event published whether the method succeeded or failed
        // Without finally: if catch re-throws, the audit event would never be published
    }
}

private void publishAuditEvent(...) {
    try {
        // Wrap in its own try-catch — NEVER let audit failure break the business operation
        eventProducer.publishEvent(BankingConstants.TOPIC_AUDIT_EVENTS, userId, auditPayload);
    } catch (Exception e) {
        log.error("Failed to publish audit event: {}", e.getMessage());
        // Swallow this exception — audit failure is not a reason to fail the business transaction
    }
}
```

```java
// shared/kafka-lib — consumer pattern
// ACK only on success, redeliver on failure
@KafkaListener(topics = "banking.transaction.events")
public void handleTransactionEvent(String event, Acknowledgment ack) {
    try {
        processEvent(event);       // business logic
        ack.acknowledge();         // SUCCESS: tell Kafka "I processed this"
        // Offset committed — this message will not be redelivered

    } catch (Exception e) {
        log.error("Failed to process event: {}", e.getMessage(), e);
        // DO NOT call ack.acknowledge()
        // Kafka retains this message and redelivers it (up to 3 times per errorHandler config)
        // After 3 failures: goes to Dead Letter Queue
    }
    // No finally needed here — we deliberately DON'T ack on failure
}
```

```java
// services/report-service — StatementService.java
// try-with-resources — automatic resource cleanup
public String generateStatement(StatementRequest request) throws Exception {
    // try-with-resources: FileOutputStream closed automatically even if exception thrown
    try (FileOutputStream fos = new FileOutputStream("/tmp/statement.pdf");
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

        // Generate PDF content
        writePdfContent(baos, request);
        baos.writeTo(fos);

        return uploadToS3(fos);  // upload and return S3 key

    }  // fos and baos automatically closed here — even if exception thrown above
    // Without try-with-resources: finally { fos.close(); baos.close(); }
    // Risk: if fos.close() throws, baos.close() never runs — resource leak
}
```

**Result:**
- `finally` in AuditAspect guarantees every method call is audited — even failed ones
- Kafka consumer only acknowledges on success — no messages silently lost on processing failures
- try-with-resources prevents file handle leaks in report generation

---

## 4. Checked vs Unchecked Exceptions

**Situation:**
Java has two types: checked (must declare/catch), unchecked (RuntimeException — optional to catch). Choosing the wrong type forces unnecessary boilerplate or hides important errors.

**Task:**
Use unchecked exceptions for programming errors and expected banking scenarios (insufficient funds, not found), checked for truly recoverable external failures.

**Action:**

```java
// ALL our custom exceptions extend RuntimeException (UNCHECKED)
// services/transaction-service — TransactionService.java

// No try-catch needed in controller — GlobalExceptionHandler handles it:
public Transaction debit(UUID accountId, UUID userId, BigDecimal amount, ...) {
    BigDecimal currentBalance = accountServiceClient.getBalance(accountId);

    if (currentBalance.compareTo(amount) < 0) {
        throw new InsufficientFundsException(accountId.toString());
        // Unchecked — doesn't force callers to add try-catch
        // GlobalExceptionHandler catches it → 422 Unprocessable Entity
    }
    // ...
}

// Controller is clean — no try-catch:
@PostMapping("/debit")
public ResponseEntity<Transaction> debit(...) {
    Transaction txn = transactionService.debit(accountId, userId, amount, ...);
    return ResponseEntity.ok(txn);
    // If InsufficientFundsException thrown: GlobalExceptionHandler handles it
}

// CHECKED exception usage — when the compiler forces us to handle it:
// services/report-service — StatementService.java
public String generateStatement(StatementRequest request) throws Exception {
    // Spring Batch's JobLauncher.run() throws JobExecutionException (checked)
    JobExecution execution = jobLauncher.run(statementJob, params);
    // Must either catch or declare throws — it's a recoverable external system failure
    return execution.getStatus().toString();
}

// @SneakyThrows — Lombok converts checked to unchecked without boilerplate:
@KafkaListener(topics = "banking.audit.events")
@SneakyThrows  // wraps checked exceptions in RuntimeException
public void consumeAuditEvent(String event, Acknowledgment ack) {
    Map<String, Object> payload = objectMapper.readValue(event, Map.class);
    // objectMapper.readValue() throws JsonProcessingException (checked)
    // @SneakyThrows: if JsonProcessingException thrown → wrapped in RuntimeException
    // No need to declare 'throws JsonProcessingException' on the method
    ack.acknowledge();
}
```

**Result:**
- Clean controller code — no try-catch everywhere because we use unchecked exceptions
- `GlobalExceptionHandler` is the single place for exception handling
- `@SneakyThrows` keeps Kafka listener signatures clean without hiding the actual error

---

## 5. Multi-catch and Exception Chaining

**Situation:**
JWT validation can fail for multiple distinct reasons — expired token, bad signature, malformed format. Each needs different logging but the same response. Exception chaining preserves the root cause.

**Task:**
Handle multiple exception types efficiently and chain exceptions to preserve context.

**Action:**

```java
// shared/security-lib — JwtTokenProvider.java
// MULTI-CATCH — different exception types, same handling
public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
        return true;

    } catch (ExpiredJwtException e) {
        log.warn("JWT token expired: {}", e.getMessage());
        // Expired is a normal event — warn level, not error

    } catch (UnsupportedJwtException | MalformedJwtException e) {
        // MULTI-CATCH with | — both handled the same way:
        log.error("Invalid JWT format: {}", e.getMessage());

    } catch (SignatureException e) {
        // DIFFERENT log level — signature failure means possible attack
        log.error("JWT signature validation FAILED — possible token forgery: {}", e.getMessage());
        // Could trigger a security alert here

    } catch (IllegalArgumentException e) {
        log.error("JWT token is null or empty: {}", e.getMessage());
    }
    return false;
}
```

```java
// EXCEPTION CHAINING — wrapping with context preservation
// services/account-service — AccountService.java

public Account getAccountById(UUID accountId) {
    try {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));

    } catch (DataAccessException ex) {
        // DataAccessException = database error (connection lost, query failed)
        // Wrap it in our exception, preserving the original as 'cause':
        throw new BankingException(
            "Database error while fetching account: " + accountId,
            "DATABASE_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex   // ← the cause: original DataAccessException preserved
        );
        // getCause() returns the original DataAccessException
        // Stack trace shows both our exception AND the root cause
    }
}
```

**Result:**
- Multi-catch for JWT exceptions: security events (SignatureException) logged at ERROR with alert potential, normal events (ExpiredJwtException) at WARN — appropriate monitoring
- Exception chaining: when a database error causes a service failure, the full root cause is in the logs — much easier to debug than a generic "account not found" message

**Interview Questions:**
- Q: What is the difference between checked and unchecked exceptions?
  A: "In our banking project, we use unchecked exceptions for all banking business exceptions — InsufficientFundsException, ResourceNotFoundException, BankingException. These extend RuntimeException. The reason: a controller method that debits an account shouldn't be forced to declare 'throws InsufficientFundsException' — that would pollute every method signature. Our GlobalExceptionHandler catches all of them. We use checked exceptions for truly external system failures — Spring Batch's JobLauncher throws JobExecutionException (checked) because job execution failure is genuinely recoverable and the caller should handle it."

- Q: What is exception chaining? Why is it useful?
  A: "In our account service, if the database goes down while fetching an account, we catch DataAccessException and throw a new BankingException with the original as the cause: `throw new BankingException(message, code, status, ex)`. This is chaining. In production logs, we see both our application-level error and the original database error — exact SQL error, connection pool timeout — making debugging much faster. Without chaining, we'd only see 'database error' with no idea what actually happened."
