# Resilience4j

## What Is Resilience4j?

A fault tolerance library for Java. Provides patterns to make your application resilient when remote services fail or are slow.

**Simple explanation:** Life insurance for your service calls. When something goes wrong, Resilience4j has a plan.

---

## Circuit Breaker

**The core pattern we use everywhere a service calls another service.**

```java
// Configuration in application.yml:
resilience4j:
  circuitbreaker:
    instances:
      account-service:            # name must match the circuit breaker name in code
        slidingWindowSize: 10     # track last 10 calls
        minimumNumberOfCalls: 5   # need at least 5 calls before calculating rate
        failureRateThreshold: 50  # if 50%+ of calls fail, open the circuit
        waitDurationInOpenState: 10s  # keep circuit open for 10 seconds
        permittedNumberOfCallsInHalfOpenState: 3  # allow 3 test calls when half-open
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

**In code — on Feign client:**
```java
@FeignClient(name = "account-service", fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")  // wraps this call with circuit breaker
    BigDecimal getBalance(@PathVariable UUID accountId);
}
```

**States:**

```
         5+ failures in last 10 calls
CLOSED ─────────────────────────────────→ OPEN
(normal)                                  (blocked)
   ↑                                         │
   │ 3 test calls succeed        10 seconds  │
   │                                         ↓
   └──────────────── HALF-OPEN ←─────────────┘
                     (testing)
```

**What happens in each state:**
- **CLOSED:** All calls go through normally
- **OPEN:** All calls immediately return the fallback (no network call attempted)
- **HALF-OPEN:** 3 test calls allowed through. If they succeed → CLOSED. If they fail → back to OPEN.

**Fallback:**
```java
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public BigDecimal getBalance(UUID accountId) {
        // Called when circuit is OPEN or when the actual call throws an exception
        log.warn("Circuit breaker OPEN for account-service, returning fallback for account: {}", accountId);
        throw new BankingException(
            "Account service temporarily unavailable. Please try again in a moment.",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
        // Alternatively: return a cached/default value if that makes sense for your use case
    }
}
```

---

## Retry

**Automatically retry failed calls with a backoff:**

```java
resilience4j:
  retry:
    instances:
      account-service:
        maxAttempts: 3            # try 3 times total (1 original + 2 retries)
        waitDuration: 500ms       # wait 500ms between retries
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        # Wait times: 500ms, 1000ms, 2000ms
        retryExceptions:
          - java.net.ConnectException  # retry on connection refused
          - java.net.SocketTimeoutException  # retry on timeout
        ignoreExceptions:
          - com.banking.common.exception.BankingException  # don't retry on our own exceptions
```

**Why exponential backoff?**
If a service is overloaded and you retry instantly, you make it more overloaded. Exponential backoff gives the service time to recover, and each retry waits longer to reduce pressure.

---

## Rate Limiter

**Prevent a service from being called too frequently:**

```java
resilience4j:
  ratelimiter:
    instances:
      fraud-check:
        limitForPeriod: 100       # max 100 calls per period
        limitRefreshPeriod: 1s    # reset count every 1 second
        timeoutDuration: 0        # don't wait — fail immediately if over limit
```

```java
@RateLimiter(name = "fraud-check", fallbackMethod = "fraudCheckFallback")
public FraudScore evaluateFraud(String transactionId, String userId, BigDecimal amount) {
    return fraudEvaluationService.evaluate(transactionId, userId, amount, null);
}

public FraudScore fraudCheckFallback(String transactionId, String userId,
                                      BigDecimal amount, RequestNotPermitted ex) {
    // Called when rate limit exceeded
    log.warn("Rate limit exceeded for fraud check, using conservative score");
    return FraudScore.builder()
            .score(0.5)
            .riskLevel(FraudScore.FraudRisk.MEDIUM)
            .reasons(new String[]{"RATE_LIMIT_EXCEEDED"})
            .build();
}
```

---

## Bulkhead

**Limit concurrent calls to a service to prevent cascading failures:**

```java
resilience4j:
  bulkhead:
    instances:
      account-service:
        maxConcurrentCalls: 20    # max 20 simultaneous calls to account-service
        maxWaitDuration: 500ms    # wait 500ms for a slot; then reject
```

**Simple analogy:** A bridge rated for 10 cars at a time. The bulkhead says "max 20 concurrent calls." If 20 are already in progress, the 21st waits 500ms. If no slot opens in 500ms, it fails fast instead of backing up and creating a traffic jam.

---

## Combining Patterns

The order matters. We apply them in this order:

```
Request
   │
   ▼
Bulkhead (reject if too many concurrent)
   │
   ▼
RateLimiter (reject if too many requests per second)
   │
   ▼
CircuitBreaker (reject if too many recent failures)
   │
   ▼
Retry (retry on transient failures)
   │
   ▼
Actual service call
```

```java
// Apply multiple patterns to one method:
@Bulkhead(name = "account-service")
@RateLimiter(name = "account-service")
@CircuitBreaker(name = "account-service", fallbackMethod = "getBalanceFallback")
@Retry(name = "account-service")
public BigDecimal getBalance(UUID accountId) {
    return accountServiceClient.getBalance(accountId);
}
```

---

## Actuator Endpoints for Resilience4j

Resilience4j exposes metrics and state via Spring Actuator:

```bash
# Check circuit breaker state:
GET /actuator/circuitbreakers
# Response:
{
  "circuitBreakers": {
    "account-service": {
      "state": "CLOSED",           # or OPEN, HALF_OPEN
      "failureRate": "2.0%",
      "slowCallRate": "0.0%",
      "numberOfBufferedCalls": 10,
      "numberOfFailedCalls": 2
    }
  }
}

# Check retry statistics:
GET /actuator/retries

# Prometheus metrics (scraped by Prometheus every 15s):
resilience4j_circuitbreaker_state{name="account-service",state="closed"} 1.0
resilience4j_circuitbreaker_failure_rate{name="account-service"} 2.0
resilience4j_retry_calls_seconds_count{name="account-service",kind="successful_without_retry"} 98.0
resilience4j_retry_calls_seconds_count{name="account-service",kind="successful_with_retry"} 2.0
```

These metrics flow into Prometheus, which Grafana displays on the circuit breaker state timeline dashboard.
