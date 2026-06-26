# Redis

## What Is Redis?

Redis = Remote Dictionary Server. An in-memory key-value store. All data lives in RAM — making it 100-1000x faster than disk-based databases.

**Simple explanation:** A giant whiteboard. You write things on it, look them up instantly. If the server restarts, the whiteboard is erased (unless persistence is configured).

**Why in-memory?** Reading from RAM takes nanoseconds. Reading from disk takes milliseconds. For operations that happen on every single HTTP request (like JWT blacklist check), this difference is critical at scale.

---

## How We Use Redis

### 1. JWT Blacklist (auth-service)

When a user logs out, their access token is added to the blacklist. Checked on every API request.

```java
// On logout:
redisTemplate.opsForValue().set(
    "blacklist:" + accessToken,  // key
    userId,                       // value (who this token belonged to)
    1,                            // TTL value
    TimeUnit.HOURS               // TTL unit — auto-deletes after 1 hour
);
// Key auto-expires after 1 hour (longer than any token's 15-min validity)

// On every request (in API Gateway filter):
Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
if (Boolean.TRUE.equals(isBlacklisted)) {
    return Response.status(401).build();
}
```

**Why Redis for this?** This check happens thousands of times per second. A database query per request would become a bottleneck. Redis answers in under 1ms.

### 2. Refresh Token Storage (auth-service)

```java
// Store refresh token after login:
redisTemplate.opsForValue().set(
    "refresh:" + refreshToken,  // key
    userId,                      // value: who owns this token
    24,                          // TTL
    TimeUnit.HOURS               // auto-deletes after 24 hours
);

// Validate on refresh request:
String storedUserId = redisTemplate.opsForValue().get("refresh:" + refreshToken);
if (storedUserId == null) {
    throw new BankingException("Invalid or expired refresh token", ...);
}

// Token rotation: delete old, create new
redisTemplate.delete("refresh:" + oldRefreshToken);
redisTemplate.opsForValue().set("refresh:" + newRefreshToken, userId, 24, TimeUnit.HOURS);
```

### 3. Account Balance Cache (account-service)

```java
// Spring's @Cacheable uses Redis under the hood:

@Cacheable(value = "account", key = "#accountId")
public Account getAccountById(UUID accountId) {
    // Only runs if NOT in cache
    return accountRepository.findById(accountId).orElseThrow(...);
}

// What Spring does internally:
// 1. Check Redis for key "account::uuid-here"
// 2. If found: deserialize and return (method body skipped)
// 3. If not found: run method, serialize result, store in Redis for 5 min, return

@CacheEvict(value = "account", key = "#accountId")
public Account updateBalance(UUID accountId, BigDecimal amount) {
    // After this runs, Redis key "account::uuid-here" is deleted
    // Next read will go to database (gets fresh data)
}
```

**TTL configuration:**
```yaml
spring.cache.redis.time-to-live: 300000  # 5 minutes in milliseconds
```

### 4. Fraud Velocity Tracking (fraud-detection-service)

```java
// Count transactions per user in the last hour
String velocityKey = "fraud:velocity:" + userId;

// INCR is atomic — safe even with 1000 concurrent requests
Long transactionCount = redisTemplate.opsForValue().increment(velocityKey);

// Set expiry only on first increment (otherwise we'd keep resetting the window)
if (transactionCount != null && transactionCount == 1) {
    redisTemplate.expire(velocityKey, 1, TimeUnit.HOURS);
}

if (transactionCount != null && transactionCount > 20) {
    score += 0.50;  // high velocity = suspicious
    reasons.add("HIGH_VELOCITY");
}
```

**Why Redis INCR for counting?**
`INCR` is atomic — if 1000 requests increment the same key simultaneously, each gets the correct new value. With a database, you'd need row-level locking to prevent race conditions.

### 5. API Rate Limiting (API Gateway)

```java
// Uses Redis token bucket algorithm
// Spring Cloud Gateway's RequestRateLimiter filter handles this automatically

// What happens in Redis:
// Key: "rate:{userId}" → tokens remaining
// Every second: refill up to burstCapacity tokens
// Every request: decrement tokens by 1
// If tokens = 0: reject with 429 Too Many Requests
```

```yaml
spring.cloud.gateway.routes:
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 10   # add 10 tokens/second
        redis-rate-limiter.burstCapacity: 20   # max 20 tokens stored
```

---

## Redis Data Structures We Use

### String (most common)
```java
// Simple key-value — used for blacklist, refresh tokens, cache
ops.set("key", "value", 1, TimeUnit.HOURS);
String value = ops.get("key");
Long count = ops.increment("counter");   // atomic increment
Boolean exists = redisTemplate.hasKey("key");
redisTemplate.delete("key");
```

### Hash (used for session data — future)
```java
// Like a HashMap stored at one key
ops.put("session:userId", "lastSeen", LocalDateTime.now().toString());
ops.put("session:userId", "ipAddress", "192.168.1.1");
Map<String, String> session = ops.entries("session:userId");
```

---

## Redis Configuration

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Serialize keys as simple strings (not Java serialization)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }
}
```

**Why StringRedisSerializer and not Java serialization?**
- Default Java serialization stores class names and version numbers
- If you rename a class, all existing cache entries become unreadable
- String serialization stores plain strings — always readable regardless of code changes

---

## ElastiCache in Production

Amazon ElastiCache is managed Redis in AWS:

```hcl
resource "aws_elasticache_replication_group" "banking" {
  replication_group_id       = "banking-redis"
  num_cache_clusters         = 3          # 1 primary + 2 replicas
  node_type                  = "cache.r6g.large"  # 13GB RAM

  at_rest_encryption_enabled  = true      # encrypt data stored on disk
  transit_encryption_enabled  = true      # encrypt data in transit (TLS)
  automatic_failover_enabled  = true      # if primary fails, replica promoted <30s

  # Multi-AZ: nodes spread across 3 availability zones
}
```

**Read replicas:** With 1 primary and 2 replicas, read operations can be distributed across 3 nodes (3x read throughput). Write operations always go to the primary.
