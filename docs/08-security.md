# 8. Security — Login, Tokens, and Roles

## The Simple Version

Imagine a hotel.

- You arrive and show your ID at **reception** (this is the login — auth-service)
- Reception gives you a **key card** (this is the JWT access token)
- The key card works for 15 minutes (short expiry keeps things secure)
- When it expires, you go back and they scan your **extended stay card** (refresh token)
- The key card only opens YOUR room, not the penthouse (this is authorization — roles)
- If you lose your key card, reception can **deactivate it** (logout/blacklist)

---

## JWT — JSON Web Token

A JWT is a special string that proves who you are. It looks like:
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyLTEyMyIsImVtYWlsIjoiam9obkBiYW5rLmNvbSIsInJvbGVzIjpbIlJPTEVfQ1VTVE9NRVIiXX0.SIGNATURE
```

Three parts separated by dots:
1. **Header** — algorithm used (HS512)
2. **Payload** — the actual data (user ID, email, roles, expiry time)
3. **Signature** — proves the token wasn't tampered with

**The signature is the key feature.** It is created using a secret key that only our server knows. If anyone modifies the payload (e.g., changes their role from CUSTOMER to ADMIN), the signature becomes invalid and the server rejects it.

Nobody can forge a valid token without knowing the secret key.

**Contents of our token payload:**
```json
{
  "sub": "user-123-abc-def",        ← the user's ID
  "email": "john@banking.com",
  "roles": ["ROLE_CUSTOMER"],
  "type": "ACCESS",
  "iat": 1705312800,                ← issued at (Unix timestamp)
  "exp": 1705313700                 ← expires at (15 minutes later)
}
```

---

## Token Lifecycle

```
1. LOGIN
   User: email + password
   Server: validates → generates access token (15 min) + refresh token (24 hr)
   Server: stores refresh token in Redis
   Browser: stores both tokens in localStorage

2. MAKING REQUESTS
   Browser → request with "Authorization: Bearer {access_token}"
   Gateway: validates token signature, checks expiry
   Gateway: checks blacklist in Redis (is it a logged-out token?)
   Gateway: extracts user ID, adds "X-User-Id" header to internal request
   Service: reads X-User-Id from header (already validated, trusted)

3. ACCESS TOKEN EXPIRES (after 15 min)
   Browser: receives 401 Unauthorized
   Browser: automatically sends refresh token to /api/v1/auth/refresh
   Server: validates refresh token
   Server: deletes old refresh token from Redis
   Server: generates new access token + new refresh token (rotation)
   Server: stores new refresh token in Redis
   Browser: stores new tokens, retries original request

4. LOGOUT
   Server: adds access token to Redis blacklist (expires in 1 hour)
   Server: deletes refresh token from Redis
   Browser: clears localStorage
```

**Why 15 minutes for access token?**
Short expiry limits the damage if a token is stolen. An attacker has at most 15 minutes before the token becomes useless. If expiry was 1 week (like some apps do), a stolen token is useful for a week.

**Why rotate refresh tokens?**
If a refresh token is stolen, the attacker can keep generating new access tokens indefinitely. With rotation, each refresh token can only be used ONCE. If the attacker uses it, the legitimate user's next refresh attempt will fail (the token is gone from Redis). The legitimate user must log in again, which alerts them that something is wrong.

---

## Password Security

Passwords are **never** stored in plaintext. They are stored as BCrypt hashes.

BCrypt is a one-way function — you can turn a password INTO a hash, but you cannot turn a hash BACK into the password.

```
"SecurePass@123" → BCrypt → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

When you log in:
1. You submit "SecurePass@123"
2. We run BCrypt on what you submitted
3. We compare the result to the stored hash
4. If they match → you know the password

Even if the entire database is stolen, the attacker has hashes, not passwords. BCrypt is deliberately slow (configurable "cost factor") to make brute-force attacks impractical.

---

## Roles and Authorization

We have 4 roles:

| Role | Who | What They Can Do |
|---|---|---|
| `ROLE_CUSTOMER` | Regular banking customers | See their own accounts, make transfers, view their transactions |
| `ROLE_TELLER` | Bank branch staff | Open accounts for customers, issue cards, view customer accounts |
| `ROLE_AUDITOR` | Compliance/audit staff | Read-only access to audit logs, cannot change anything |
| `ROLE_ADMIN` | System administrators | Everything — freeze accounts, manage users, view all fraud alerts |

**How authorization works in the code:**
```java
@GetMapping("/user/{userId}")
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
public ResponseEntity<...> getUserAccounts(@PathVariable UUID userId) {
    // Spring Security checks the JWT's roles before this method runs
    // If the role check fails → 403 Forbidden, method never runs
}
```

The `@PreAuthorize` annotation is checked by Spring Security before the method body runs. The roles come from the JWT token — no database query needed.

---

## Account Lockout

After 5 failed login attempts:
1. Account is locked
2. `locked_until` is set to 30 minutes in the future
3. Further attempts return: "Account locked. Try again later."
4. After 30 minutes, the lock automatically lifts on the next attempt

This prevents brute-force attacks (trying thousands of passwords automatically).

---

## CORS — Cross-Origin Resource Security

**Simple explanation:** Your browser, by default, refuses to let a webpage at `evilhacker.com` make requests to `yourbank.com`. CORS is the mechanism that controls which websites ARE allowed to make requests.

Our allowed origins:
- `http://localhost:5173` (local development)
- `http://localhost:3000` (local development alternative)
- `https://app.bankingapp.com` (production frontend)

Any request from `evilhacker.com` would be blocked before it reaches our services.

CORS is configured at two levels:
1. **API Gateway** — global CORS config for all routes
2. **Individual services** — `CorsConfig` bean as backup

---

## HTTPS / TLS

All external communication uses TLS (HTTPS). This encrypts data in transit — even if someone intercepts the network packets, they see only encrypted gibberish.

Between internal services (inside Kubernetes), traffic is optionally encrypted using mTLS (mutual TLS) — both sides authenticate each other.

On AWS MSK (Kafka), `TLS` is set as the broker-to-client protocol.
