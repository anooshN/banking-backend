# Module 9 — Spring Security, JPA & Spring Cloud

---

## Spring Security

### JWT Authentication Flow

**Situation:**
Every API request must be authenticated. Checking the database on every request would add 50ms and overload the database at scale.

**Task:**
Use stateless JWT authentication — validate the token locally without a database call.

**Action:**

```java
// shared/security-lib — JwtAuthenticationFilter.java
// Runs on EVERY request BEFORE reaching the controller
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. Extract token from header
            String jwt = extractJwt(request); // "Bearer eyJhbGci..." → "eyJhbGci..."

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // 2. validateToken: checks signature (no DB call!) + expiry
                String userId = jwtTokenProvider.extractUserId(jwt);
                List<String> roles = jwtTokenProvider.extractRoles(jwt);

                // 3. Build authentication object with roles
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // 4. Store in SecurityContext — available to controllers
                SecurityContextHolder.getContext().setAuthentication(auth);
                // Now @PreAuthorize("hasRole('ADMIN')") works
            }
        } catch (Exception ex) {
            log.error("Auth error: {}", ex.getMessage());
            // Don't block — let Spring Security handle unauthorized responses
        }
        filterChain.doFilter(request, response); // continue to controller
    }
}

// SecurityContext is ThreadLocal — safe for concurrent requests
// Thread 47's SecurityContext has userId=user-123, roles=[ROLE_CUSTOMER]
// Thread 48's SecurityContext has userId=user-456, roles=[ROLE_ADMIN]
// No interference between threads
```

### @PreAuthorize — Role-Based Access Control

```java
// services/account-service — AccountController.java
@GetMapping("/user/{userId}")
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
// Spring Security evaluates this BEFORE the method runs
// Gets roles from SecurityContextHolder (set by JwtAuthenticationFilter)
// If check fails → 403 Forbidden, method never executes
public ResponseEntity<ApiResponse<List<Account>>> getUserAccounts(@PathVariable UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(accountService.getAccountsByUserId(userId)));
}

@PatchMapping("/{accountId}/freeze")
@PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
// Customers cannot freeze accounts — only staff
public ResponseEntity<ApiResponse<Account>> freezeAccount(@PathVariable UUID accountId) {
    return ResponseEntity.ok(ApiResponse.success(accountService.freezeAccount(accountId)));
}

// More complex SpEL expression:
@GetMapping("/{userId}/profile")
@PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.principal")
// ADMIN can see anyone's profile
// OR user can see their OWN profile (#userId matches their token's sub)
// authentication.principal = userId string set in JwtAuthenticationFilter
public ResponseEntity<UserProfile> getProfile(@PathVariable UUID userId) { ... }
```

---

## Spring Data JPA — Key Topics

### Derived Query Methods

```java
// services/account-service — AccountRepository.java
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Spring Data generates SQL from method name:
    List<Account> findByUserId(UUID userId);
    // → SELECT * FROM accounts WHERE user_id = ?

    Optional<Account> findByAccountNumber(String accountNumber);
    // → SELECT * FROM accounts WHERE account_number = ? LIMIT 1

    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);
    // → SELECT * FROM accounts WHERE user_id = ? AND status = ?

    List<Account> findByBalanceGreaterThanAndStatus(BigDecimal amount, Account.AccountStatus status);
    // → SELECT * FROM accounts WHERE balance > ? AND status = ?

    long countByUserId(UUID userId);
    // → SELECT COUNT(*) FROM accounts WHERE user_id = ?

    boolean existsByAccountNumber(String accountNumber);
    // → SELECT COUNT(*) > 0 FROM accounts WHERE account_number = ?

    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);
    // Custom JPQL — use when method name would be too complex

    @Query(value = "SELECT * FROM accounts WHERE balance > :amount ORDER BY balance DESC LIMIT 10",
           nativeQuery = true)
    List<Account> findTopAccountsByBalance(@Param("amount") BigDecimal amount);
    // nativeQuery=true — raw SQL (PostgreSQL specific features allowed)
}
```

### The N+1 Problem

```java
// THE PROBLEM:
// User entity has a Set<String> roles (ElementCollection)
// services/auth-service — UserRepository

// BAD — generates N+1 queries:
List<User> users = userRepository.findAll();  // Query 1: SELECT * FROM users (100 users)
for (User user : users) {
    Set<String> roles = user.getRoles();  // Query 2-101: SELECT * FROM user_roles WHERE user_id = ?
    // 100 users = 100 separate queries for roles = 101 total queries!
}

// GOOD — JOIN FETCH: 1 query total:
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles")
List<User> findAllWithRoles();
// → SELECT u.*, r.* FROM users u LEFT JOIN user_roles r ON u.id = r.user_id
// 1 query, all data retrieved at once

// For single user with roles:
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
Optional<User> findByEmailWithRoles(@Param("email") String email);
// Used in login — we always need the roles to generate JWT
```

### Flyway Migrations

```sql
-- services/auth-service/src/main/resources/db/migration/V1__create_users_table.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id              UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password        VARCHAR(255) NOT NULL,
    -- ... other columns
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- V2__add_mfa_columns.sql (added later in development):
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(255);
-- Flyway runs this ONCE, records in flyway_schema_history table
-- Never runs again even if app restarts (checksum match)
```

---

## Spring Cloud — Key Topics

### Eureka — Service Discovery

```java
// HOW SERVICES FIND EACH OTHER:

// Step 1: auth-service starts and registers:
// POST http://localhost:8761/eureka/apps/AUTH-SERVICE
// Body: { host: "10.0.0.42", port: 8081, status: "UP" }

// Step 2: Every 30 seconds, auth-service sends heartbeat:
// PUT http://localhost:8761/eureka/apps/AUTH-SERVICE/10.0.0.42:auth-service:8081

// Step 3: transaction-service needs account-service:
// transaction-service's Feign client: @FeignClient(name = "account-service")
// Spring Cloud LoadBalancer asks Eureka: "where is account-service?"
// Eureka returns: [10.0.0.42:8083, 10.0.0.43:8083] (two instances)
// LoadBalancer picks one (round-robin)
// Feign makes HTTP call to chosen instance

// If account-service instance at 10.0.0.42 crashes:
// Eureka waits 90 seconds (3 missed heartbeats at 30s interval)
// Removes 10.0.0.42:8083 from registry
// Next LoadBalancer call only gets 10.0.0.43:8083
```

### Feign Client — Declarative REST

```java
// services/transaction-service
// PROBLEM: HTTP calls between services require RestTemplate boilerplate
// SOLUTION: Feign — write an interface, Feign writes the HTTP code

@FeignClient(
    name = "account-service",          // Eureka service name to call
    fallback = AccountServiceClientFallback.class  // when account-service is down
)
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")  // open circuit after failures
    BigDecimal getBalance(@PathVariable UUID accountId);
    // Feign generates: GET http://{account-service-address}/api/v1/accounts/{id}/balance
    // Handles: serialization, deserialization, error handling, load balancing

    @PatchMapping("/api/v1/accounts/{accountId}/balance")
    void updateBalance(@PathVariable UUID accountId, @RequestParam BigDecimal amount);
}

// Fallback — when account-service is down:
@Component
public class AccountServiceClientFallback implements AccountServiceClient {
    @Override
    public BigDecimal getBalance(UUID accountId) {
        // Can't safely process transaction without balance — throw
        throw new BankingException("Account service down", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
    @Override
    public void updateBalance(UUID accountId, BigDecimal amount) {
        throw new BankingException("Account service down", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
```

### Circuit Breaker States

```
Normal operation → CLOSED state (all calls go through)
     ↓
5+ failures in 10 calls (50% threshold)
     ↓
OPEN state (all calls return fallback IMMEDIATELY — no network call)
     ↓
After 10 seconds, 3 test calls allowed
     ↓
Tests succeed → CLOSED (back to normal)
Tests fail    → OPEN (stay blocked, reset timer)

RESULT: If account-service is down:
- Without circuit breaker: transaction-service waits 30s per call → thread pool exhausted → cascade failure
- With circuit breaker: OPEN state returns fallback in <1ms → thread pool protected → no cascade
```

**Interview Questions:**

- Q: How does JWT authentication work in your project?
  A: "In our banking project, the situation was that checking a database on every API request would add 50ms and create a bottleneck at scale. The task was stateless authentication. We implemented JWT: at login, we generate a signed token containing userId, email, roles, and expiry. For every subsequent request, the JwtAuthenticationFilter extracts the token from the Authorization header, validates the signature using our secret key (no database call), extracts the userId and roles, and sets them in SecurityContextHolder. Controllers use @PreAuthorize to check roles from that context. The result: authentication adds under 1ms per request — the JWT signature verification is pure computation, no I/O."

- Q: What is the N+1 query problem? How did you solve it?
  A: "In our auth service, when loading users, the User entity has a `Set<String> roles` as a separate table (user_roles). Without optimization, loading 100 users generates query 1 for users, then 100 separate queries for each user's roles — 101 total. We solved it with JOIN FETCH in JPQL: `SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email`. This fetches the user and their roles in a single SQL JOIN. The result: the login query went from 2 queries (user + roles) to 1 query — critical when handling 1000 logins per second."
