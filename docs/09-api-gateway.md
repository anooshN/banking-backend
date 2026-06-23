# 9. The API Gateway

## The Simple Version

The API Gateway is like the reception desk at a big company.

Everyone who walks in (every request) goes through reception first. Reception:
1. Checks your ID (JWT validation)
2. Decides if you are allowed in (authorization)
3. Tells you which floor to go to (routing)
4. Stops you if you are making too many visits in one day (rate limiting)
5. Turns you away if the department you want is closed (circuit breaking)

---

## Why a Gateway?

Without a gateway, the frontend would need to:
- Know the address of all 14 services
- Handle authentication in every API call separately
- Deal with CORS for every service separately
- Handle unavailable services itself

With a gateway:
- Frontend knows ONE address: `https://api.bankingapp.com`
- Authentication is handled once at the gateway
- CORS is configured once
- Service failures are handled gracefully with fallbacks

---

## Request Journey Through the Gateway

```
Browser sends: GET /api/v1/accounts/user/123
               Authorization: Bearer eyJhbGci...

Step 1 — CORS
  Is the request from an allowed origin?
  Yes (localhost:5173) → continue
  No (evilsite.com) → reject with 403

Step 2 — AuthenticationFilter
  Is there an Authorization header?
    No AND this isn't a public path (like /auth/login) → reject with 401
    Yes → extract the JWT token

  Is the JWT signature valid?
    No → reject with 401
    Yes → extract userId from claims

  Is this token on the blacklist in Redis?
    Yes (user logged out) → reject with 401
    No → continue

  Add X-User-Id header to the request for downstream services

Step 3 — Rate Limiting
  How many requests has this user made in the last second?
  Config: 10/second, burst up to 20
  If over limit → reject with 429 Too Many Requests

Step 4 — Routing
  Path starts with /api/v1/accounts/ ?
  → Forward to account-service (load balanced via Eureka)
  
  If multiple instances of account-service are running,
  Spring Cloud LoadBalancer picks one (round-robin by default)

Step 5 — Circuit Breaker
  Did account-service respond?
    Yes → return the response to the browser
    No (timeout or error) →
      Is the circuit open? (5+ failures in the last 10 calls)
        Yes → immediately return fallback response (no wasted time waiting)
        No → try the request (failure increments the counter)
```

---

## Route Configuration

Each route is defined in `application.yml`:

```yaml
routes:
  - id: account-service
    uri: lb://account-service      ← lb:// means "load balance via Eureka"
    predicates:
      - Path=/api/v1/accounts/**   ← match this URL pattern
    filters:
      - AuthenticationFilter       ← custom filter: JWT validation
      - name: CircuitBreaker
        args:
          name: account-service
          fallbackUri: forward:/fallback/account
```

`lb://account-service` means: look up "account-service" in Eureka, get its list of instances, pick one using load balancing.

---

## Rate Limiting

Built with Redis. Uses the **Token Bucket** algorithm:

**Simple explanation:** You have a bucket that holds 20 coins. Every second, 10 new coins are added (up to 20 max). Every request costs 1 coin. If the bucket is empty, the request is rejected.

This allows:
- Normal usage: 10 requests per second, indefinitely
- Short burst: up to 20 requests instantly (using saved-up coins)
- Protection from bots/abusers: they hit the limit quickly

Configuration:
```yaml
redis-rate-limiter.replenishRate: 10    ← add 10 coins per second
redis-rate-limiter.burstCapacity: 20    ← bucket holds 20 coins
redis-rate-limiter.requestedTokens: 1   ← each request costs 1 coin
```

The auth endpoint has stricter limits than other endpoints, since it is the most common attack target.

---

## Fallback Responses

When a service is unavailable, instead of the browser seeing a cryptic error, the gateway returns a helpful message:

```java
@GetMapping("/fallback/account")
public ResponseEntity<ApiResponse<Void>> accountFallback() {
    return ResponseEntity.status(503)
        .body(ApiResponse.error(
            "Account service is currently unavailable. Please try again in a moment.",
            "SERVICE_UNAVAILABLE"
        ));
}
```

This is much better for the user than seeing a timeout error or a stack trace.

---

## Aggregated Swagger Documentation

The gateway aggregates API documentation from all services:

```yaml
springdoc:
  swagger-ui:
    urls:
      - name: Auth Service
        url: /api/v1/auth/v3/api-docs
      - name: Account Service
        url: /api/v1/accounts/v3/api-docs
      - name: Transaction Service
        url: /api/v1/transactions/v3/api-docs
```

Visiting `http://localhost:8080/swagger-ui.html` shows all endpoints from all services in one place, with a dropdown to switch between services.
