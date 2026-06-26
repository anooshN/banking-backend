# Spring Cloud — Eureka, Config, Gateway, Feign

## What Is Spring Cloud?

Spring Cloud is a set of tools for building distributed systems (microservices). Each tool solves one specific problem that arises when you have many services instead of one.

---

## Eureka — Service Discovery

### The Problem Without Eureka

```
// BAD: hardcoded address
String accountServiceUrl = "http://10.0.0.42:8083";
// What if the server at 10.0.0.42 crashes?
// What if we start a second account-service instance at 10.0.0.43?
// What if Kubernetes assigns a new IP every restart?
```

### How Eureka Fixes It

**Server side (discovery-server):**
```java
@SpringBootApplication
@EnableEurekaServer        // this JVM becomes the Eureka registry
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

**Client side (every other service):**
```java
@SpringBootApplication
@EnableDiscoveryClient     // this service registers with Eureka
public class AccountServiceApplication { ... }
```

```yaml
# application.yml on every client service
eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka-secret@localhost:8761/eureka/
  instance:
    prefer-ip-address: true        # register with IP, not hostname
    lease-renewal-interval-in-seconds: 10    # heartbeat every 10 seconds
    lease-expiration-duration-in-seconds: 30 # removed if no heartbeat for 30s
```

**What happens:**
1. account-service starts → sends POST to Eureka: "I am account-service, I am at 10.0.0.42:8083"
2. Every 10 seconds: account-service sends heartbeat to Eureka
3. transaction-service needs account-service → asks Eureka: "Where is account-service?"
4. Eureka replies: ["10.0.0.42:8083", "10.0.0.43:8083"] (if 2 instances running)
5. Spring Cloud LoadBalancer picks one (round-robin)
6. If account-service crashes → next heartbeat is missed → Eureka removes it after 30s

---

## Spring Cloud Config — Centralized Configuration

### The Problem

14 services, each with their own application.yml. Changing the Redis host address requires updating 14 files and redeploying 14 services.

### How Config Server Fixes It

**Config server reads from banking-config Git repo and serves configs to all services:**

```java
@SpringBootApplication
@EnableConfigServer        // this is the config server
public class ConfigServerApplication { ... }
```

```yaml
# config-server/application.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/anooshN/banking-config
          search-paths: '{application}'  # look in {service-name}/ folder
```

**Each client service fetches config on startup:**
```yaml
# auth-service/application.yml (minimal — most config comes from config server)
spring:
  application:
    name: auth-service           # used to find auth-service/auth-service.yml in config repo
  config:
    import: optional:configserver:http://config:config-secret@localhost:8888
    # optional: = don't fail if config server is unreachable (use local defaults)
```

**Config server resolution order for auth-service in dev profile:**
1. `application.yml` (shared, all services)
2. `application-dev.yml` (shared dev overrides)
3. `auth-service/auth-service.yml` (service-specific defaults)
4. `auth-service/auth-service-dev.yml` (service-specific dev overrides)

Later files override earlier ones. Properties in step 4 override step 1.

**Encrypted values:**
```bash
# Encrypt a secret (call config server's /encrypt endpoint):
curl -X POST http://localhost:8888/encrypt -d 'my-database-password'
# Returns: AQBx3r7i...encrypted...value

# Store in config file:
spring:
  datasource:
    password: '{cipher}AQBx3r7i...encrypted...value'
# Config server decrypts this automatically before sending to the service
```

---

## Spring Cloud Gateway — API Gateway

### Route Configuration

```yaml
# api-gateway/application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: account-service          # unique name for this route
          uri: lb://account-service    # lb:// = load-balanced via Eureka
          predicates:
            - Path=/api/v1/accounts/** # match all paths starting with this
          filters:
            - AuthenticationFilter     # custom filter: validates JWT
            - name: CircuitBreaker     # built-in circuit breaker filter
              args:
                name: account-service
                fallbackUri: forward:/fallback/account
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

### Custom Gateway Filter

```java
// Our JWT validation filter for the gateway
@Component
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    // List of paths that don't need a JWT
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/actuator", "/swagger-ui", "/v3/api-docs"
    );

    @Override
    public GatewayFilter apply(Config config) {
        // This lambda runs for every request matching the route
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().toString();

            // Skip JWT check for public paths
            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest()
                    .getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // No Authorization header → reject
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange);
            }

            String token = authHeader.substring(7);

            // Invalid JWT signature or expired → reject
            if (!jwtTokenProvider.validateToken(token)) {
                return unauthorized(exchange);
            }

            // Check blacklist (logged-out tokens)
            if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token))) {
                return unauthorized(exchange);
            }

            // Valid! Extract userId and add to headers for downstream services
            String userId = jwtTokenProvider.extractUserId(token);
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-User-Id", userId)  // downstream services read this
                            .build())
                    .build();

            // Continue to next filter / the target service
            return chain.filter(mutatedExchange);
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {}  // configuration class (empty — no config needed)
}
```

**Why is the Gateway reactive (Mono, Flux)?**
Spring Cloud Gateway is built on Spring WebFlux (reactive), not Spring MVC (blocking). The gateway handles thousands of concurrent connections efficiently using a small number of threads. Each request is represented as a `Mono<Void>` (a publisher of a single async result).

---

## OpenFeign — Declarative HTTP Client

### The Problem

Making HTTP calls between services without Feign:
```java
// WITHOUT Feign — verbose and error-prone
RestTemplate restTemplate = new RestTemplate();
String url = "http://account-service/api/v1/accounts/" + accountId + "/balance";
try {
    ResponseEntity<BigDecimal> response = restTemplate.getForEntity(url, BigDecimal.class);
    return response.getBody();
} catch (HttpClientErrorException e) {
    // handle errors
} catch (ResourceAccessException e) {
    // handle connection refused
}
```

### With Feign

```java
// 1. Enable Feign on the application class:
@SpringBootApplication
@EnableFeignClients
public class TransactionServiceApplication { ... }

// 2. Define an interface — Feign generates all the HTTP code:
@FeignClient(
    name = "account-service",           // Eureka service name to call
    fallback = AccountServiceClientFallback.class  // what to do if down
)
public interface AccountServiceClient {

    // Maps to: GET http://account-service/api/v1/accounts/{accountId}/balance
    @GetMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")
    BigDecimal getBalance(@PathVariable UUID accountId);

    // Maps to: PATCH http://account-service/api/v1/accounts/{accountId}/balance?amount={amount}
    @PatchMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")
    void updateBalance(@PathVariable UUID accountId, @RequestParam BigDecimal amount);
}

// 3. Fallback — what to return when account-service is down:
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public BigDecimal getBalance(UUID accountId) {
        // Can't get balance, can't process transaction safely
        throw new BankingException(
            "Account service unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Override
    public void updateBalance(UUID accountId, BigDecimal amount) {
        throw new BankingException(
            "Account service unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}

// 4. Use it — looks just like calling a local method:
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final AccountServiceClient accountServiceClient;

    public Transaction debit(UUID accountId, UUID userId, BigDecimal amount, ...) {
        // This makes an HTTP GET to account-service — looks like a method call
        BigDecimal currentBalance = accountServiceClient.getBalance(accountId);

        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId.toString());
        }
        // ...
    }
}
```

**What Feign does automatically:**
- Looks up "account-service" address in Eureka
- Picks one instance if multiple are running (load balancing)
- Serializes parameters to URL/body
- Deserializes JSON response to Java objects
- Handles HTTP error codes (4xx → exception, 5xx → fallback)
- Integrates with Resilience4j circuit breaker
- Logs requests/responses in DEBUG mode

---

## Spring Cloud LoadBalancer

**Automatically distributes requests across multiple instances of a service.**

```
2 instances of account-service running:
  Instance A: 10.0.0.42:8083
  Instance B: 10.0.0.43:8083

Feign call 1 → Instance A
Feign call 2 → Instance B
Feign call 3 → Instance A
Feign call 4 → Instance B
(round-robin)
```

**Configuration:**
```yaml
spring:
  cloud:
    loadbalancer:
      retry:
        enabled: true                    # retry on a different instance if one fails
        max-retries-on-same-service-instance: 0
        max-retries-on-next-service-instance: 2
```

When Instance A returns a 500 error or connection refused, Spring Cloud LoadBalancer automatically retries on Instance B — transparent to the calling code.
