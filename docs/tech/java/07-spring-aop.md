# Spring AOP (Aspect-Oriented Programming)

## What Is AOP?

**Simple explanation:** AOP lets you say "before/after/around any method with this annotation, run this code."

Imagine you want to log every time a door opens and closes in a building. Without AOP, you'd put logging code in every doorknob. With AOP, you add one sensor to the security system and it logs all doors automatically.

In code: instead of adding audit logging, cache handling, or transaction management to every method, you write it once in an "Aspect" and annotate methods.

---

## @Auditable — How We Use AOP

**Step 1: Define the annotation (the label we stick on methods):**
```java
// shared/audit-lib — Auditable.java
@Target(ElementType.METHOD)    // can be placed on methods
@Retention(RetentionPolicy.RUNTIME)  // available at runtime (needed for AOP)
public @interface Auditable {
    String action();    // e.g., "DEBIT", "CREATE_ACCOUNT", "FREEZE_ACCOUNT"
    String resource();  // e.g., "Transaction", "Account"
}
```

**Step 2: Write the Aspect (the code that runs around annotated methods):**
```java
// shared/audit-lib — AuditAspect.java
@Slf4j
@Aspect      // marks this as an AOP aspect
@Component   // Spring-managed bean
@RequiredArgsConstructor
public class AuditAspect {

    private final BankingEventProducer eventProducer;

    // Pointcut: runs around (@Around) any method annotated with @Auditable
    // @annotation(auditable) — gives us access to the annotation's values
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable)
            throws Throwable {

        // Get who is doing this (from Spring Security context)
        String userId = extractUserId();
        String correlationId = MDC.get("correlationId");  // from CorrelationIdFilter
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;

        try {
            // ← This is where the actual method runs
            result = joinPoint.proceed();
            return result;  // return whatever the method returned

        } catch (Throwable ex) {
            status = "FAILURE";
            errorMessage = ex.getMessage();
            throw ex;  // re-throw so the caller still gets the exception

        } finally {
            // This always runs, success or failure
            long durationMs = System.currentTimeMillis() - startTime;
            publishAuditEvent(auditable, userId, correlationId, status, errorMessage, durationMs);
        }
    }

    private void publishAuditEvent(Auditable auditable, String userId,
                                    String correlationId, String status,
                                    String errorMessage, long durationMs) {
        try {
            Map<String, Object> auditPayload = new HashMap<>();
            auditPayload.put("action", auditable.action());        // "DEBIT"
            auditPayload.put("resource", auditable.resource());    // "Transaction"
            auditPayload.put("userId", userId);
            auditPayload.put("correlationId", correlationId);
            auditPayload.put("status", status);                    // "SUCCESS" or "FAILURE"
            auditPayload.put("errorMessage", errorMessage);
            auditPayload.put("durationMs", durationMs);
            auditPayload.put("timestamp", LocalDateTime.now().toString());

            // Publish to Kafka — audit-service will persist to Cassandra
            eventProducer.publishEvent(
                BankingConstants.TOPIC_AUDIT_EVENTS,
                userId,
                auditPayload
            );
        } catch (Exception e) {
            // NEVER let audit failure break the actual business operation
            log.error("Failed to publish audit event: {}", e.getMessage());
        }
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated())
                ? auth.getName()
                : "anonymous";
    }
}
```

**Step 3: Use the annotation (clean business logic with no audit code):**
```java
// account-service — AccountService.java
@Service
public class AccountService {

    // Just add the annotation — AuditAspect runs automatically around this method
    @Transactional
    @CacheEvict(value = "account", key = "#accountId")
    @Auditable(action = "UPDATE_BALANCE", resource = "Account")
    public Account updateBalance(UUID accountId, BigDecimal amount) {
        // Pure business logic — no audit code needed here
        Account account = accountRepository.findById(accountId).orElseThrow(...);
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    @CacheEvict(value = "account", key = "#accountId")
    @Auditable(action = "FREEZE_ACCOUNT", resource = "Account")
    public Account freezeAccount(UUID accountId) {
        Account account = getAccountById(accountId);
        account.setStatus(Account.AccountStatus.FROZEN);
        return accountRepository.save(account);
    }
}
```

---

## How AOP Works Internally

When Spring sees `@Auditable` on a method, it creates a **proxy** around the bean:

```
Your code calls: accountService.updateBalance(id, amount)

What actually happens:
1. Call goes to the AOP PROXY (not the real method)
2. Proxy runs: AuditAspect.auditMethod() BEFORE the real method
3. Proxy calls: joinPoint.proceed() → real updateBalance() runs
4. Real method returns
5. Proxy runs: finally block (publish audit event)
6. Proxy returns result to your code

You see: just a method call
Reality: wrapped in audit logging transparently
```

**CGLIB proxies:** Spring creates a subclass of your bean at runtime. The proxy overrides every method to add the AOP behavior. That's why:
- `@Service` classes cannot be `final` (can't subclass a final class)
- Internal calls (`this.updateBalance()`) bypass AOP (calling real class, not proxy)

---

## Other AOP Uses in This Project

**Spring's built-in AOP annotations (all use the same mechanism):**

```java
// @Transactional — wraps method in a database transaction
@Transactional
public Account createAccount(...) { ... }
// AOP begins transaction before, commits/rolls back after

// @Cacheable — checks cache before, stores in cache after
@Cacheable(value = "account", key = "#accountId")
public Account getAccountById(UUID accountId) { ... }
// AOP checks Redis before calling the method
// If found in Redis: returns cached value without running method body

// @CacheEvict — clears cache entry after method runs
@CacheEvict(value = "account", key = "#accountId")
public Account updateBalance(UUID accountId, BigDecimal amount) { ... }
// AOP deletes Redis key after method completes

// @PreAuthorize — checks security before method runs
@PreAuthorize("hasRole('ADMIN')")
public void deleteAccount(UUID accountId) { ... }
// AOP checks Spring Security context before running the method
// If check fails: throws AccessDeniedException → returns 403
```

---

## @Pointcut — Reusable Pointcut Expressions

```java
@Aspect
@Component
public class LoggingAspect {

    // Define reusable pointcut expression
    @Pointcut("execution(* com.banking.*.service.*Service.*(..))")
    // Matches: any method (*)
    //          in package com.banking.*.service
    //          in any class ending with Service
    //          with any parameters (..)
    public void serviceLayerPointcut() {}

    @Pointcut("@annotation(com.banking.audit.annotation.Auditable)")
    public void auditablePointcut() {}

    // Use the pointcut by name
    @Before("serviceLayerPointcut()")
    public void logServiceCall(JoinPoint joinPoint) {
        log.debug("Calling: {}.{}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
    }

    // Combine pointcuts with &&, ||, !
    @AfterThrowing(
        pointcut = "serviceLayerPointcut() && !auditablePointcut()",
        throwing = "ex"
    )
    public void logServiceException(JoinPoint joinPoint, Exception ex) {
        // Runs after any service method throws an exception
        // BUT NOT if it has @Auditable (AuditAspect handles that case)
        log.error("Exception in {}: {}",
                joinPoint.getSignature().getName(),
                ex.getMessage());
    }
}
```

---

## Types of Advice

| Annotation | When It Runs |
|---|---|
| `@Before` | Before the method, cannot stop execution |
| `@After` | After the method (always, success or failure) |
| `@AfterReturning` | After the method returns successfully |
| `@AfterThrowing` | After the method throws an exception |
| `@Around` | Wraps the entire method — most powerful, can modify args/return value |

We use `@Around` for `@Auditable` because we need:
- The return value (to include in audit)
- The exception (to record FAILURE status)
- Timing (duration in ms)
- The ability to re-throw the exception
