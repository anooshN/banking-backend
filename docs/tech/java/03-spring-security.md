# Spring Security

## What Is Spring Security?

A framework that handles authentication (who are you?) and authorization (what are you allowed to do?) in Spring applications.

**Simple explanation:** A bouncer and a guest list. Spring Security checks your ID at the door (JWT validation) and checks if your name is on the list for the specific area you're trying to enter (role check).

---

## Security Filter Chain

Every HTTP request passes through a chain of filters before reaching your controller. We customize this chain:

```java
// In each service — SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // enables @PreAuthorize on methods
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            // Disable CSRF: we use stateless JWTs, not session cookies
            // CSRF protection is for session-based apps — not needed here
            .csrf(csrf -> csrf.disable())

            // Don't create HTTP sessions — each request is self-contained (JWT)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CORS configuration (allows frontend to call this API)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // URL-based authorization rules
            .authorizeHttpRequests(auth -> auth
                // These paths require NO authentication
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/actuator/health/**",  // Kubernetes health probes
                    "/v3/api-docs/**",      // Swagger
                    "/swagger-ui/**"
                ).permitAll()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Add our JWT filter BEFORE Spring's UsernamePasswordAuthenticationFilter
            // This means JWT is checked first on every request
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 10 (2^10 = 1024 iterations)
        // Higher = more secure but slower
        // 10 is the industry standard — takes ~100ms to hash, making brute-force impractical
        return new BCryptPasswordEncoder(10);
    }
}
```

---

## JWT Authentication Filter

Runs on EVERY request. Validates the token and sets up the security context:

```java
// shared/security-lib — JwtAuthenticationFilter.java
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // OncePerRequestFilter guarantees this runs exactly once per request
    // (some filters can run multiple times per request in some frameworks)

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Extract token from "Authorization: Bearer eyJhbGci..." header
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // 2. Parse claims from JWT (user ID, roles)
                String userId = jwtTokenProvider.extractUserId(jwt);
                List<String> roles = jwtTokenProvider.extractRoles(jwt);

                // 3. Convert role strings to Spring Security's GrantedAuthority
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // 4. Create authentication object — no password needed (JWT already proves identity)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,       // principal (who is this?)
                                null,         // credentials (not needed — JWT is the credential)
                                authorities   // what they're allowed to do
                        );

                // 5. Store in SecurityContext — available to the controller
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Now @PreAuthorize, hasRole(), etc. will work in controllers
            }
        } catch (Exception ex) {
            // Don't throw — just log. The request continues.
            // If SecurityContext is empty, Spring Security will return 401 automatically.
            log.error("Could not set user authentication: {}", ex.getMessage());
        }

        // 6. Continue to next filter (or controller)
        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        // Check "Authorization: Bearer eyJhbGci..."
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // remove "Bearer " prefix
        }
        return null;
    }
}
```

---

## JwtTokenProvider — Creating and Validating Tokens

```java
// shared/security-lib — JwtTokenProvider.java
@Component
public class JwtTokenProvider {

    @Value("${banking.jwt.secret}")
    private String jwtSecret;

    @Value("${banking.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;  // 15 minutes

    // HMAC-SHA512 signing key derived from the secret
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
        // jwtSecret must be at least 64 bytes for HS512
        // Our secret is 64+ chars in production
    }

    public String generateAccessToken(String userId, String email, List<String> roles) {
        return Jwts.builder()
                .setSubject(userId)               // "sub" claim — who this token is for
                .addClaims(Map.of(
                        "email", email,
                        "roles", roles,           // ["ROLE_CUSTOMER"]
                        "type", "ACCESS"          // distinguish from refresh tokens
                ))
                .setIssuedAt(new Date())          // "iat" — when issued
                .setExpiration(new Date(          // "exp" — when it expires
                        System.currentTimeMillis() + accessTokenExpirationMs
                ))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512) // sign with our secret
                .compact();                       // serialize to compact JWT string
    }

    public boolean validateToken(String token) {
        try {
            // This does ALL validation in one call:
            // 1. Verifies the signature (was this signed by us?)
            // 2. Checks expiration (is it still valid?)
            // 3. Checks format (is it a valid JWT?)
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT: {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            // This means someone tried to forge a token!
        }
        return false;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();  // the payload (sub, email, roles, iat, exp)
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) extractAllClaims(token).get("roles");
    }
}
```

---

## Method-Level Security — @PreAuthorize

**Authorization at the method level, not just URL level:**

```java
// AccountController.java
@GetMapping("/{accountId}")
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
// Spring Security evaluates this BEFORE the method body runs
// If the check fails → 403 Forbidden is returned, method never executes
public ResponseEntity<ApiResponse<Account>> getAccount(@PathVariable UUID accountId) {
    return ResponseEntity.ok(ApiResponse.success(accountService.getAccountById(accountId)));
}

@DeleteMapping("/{accountId}")
@PreAuthorize("hasRole('ADMIN')")  // only ADMIN can delete accounts
public ResponseEntity<Void> deleteAccount(@PathVariable UUID accountId) {
    accountService.deleteAccount(accountId);
    return ResponseEntity.noContent().build();
}

// More complex expressions using Spring EL:
@GetMapping("/user/{userId}/accounts")
@PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.principal")
// ADMIN can see any user's accounts
// A customer can only see their OWN accounts (userId matches their JWT sub)
public ResponseEntity<List<Account>> getUserAccounts(@PathVariable UUID userId) { ... }
```

**SpEL (Spring Expression Language) in @PreAuthorize:**
```java
// authentication.principal — the userId string from JWT (set by JwtAuthenticationFilter)
// authentication.authorities — the list of roles
// #userId — refers to the method parameter named 'userId'
// hasRole('ADMIN') — checks if 'ROLE_ADMIN' is in authorities
// hasAnyRole('ADMIN', 'TELLER') — checks if any of these roles match
```

---

## BCrypt Password Hashing

```java
// Registration — hash before storing
@Transactional
public AuthResponse register(RegisterRequest request) {
    User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            //        ↑ turns "SecurePass@123" into "$2a$10$N9qo8uLOi..."
            //          NEVER store plaintext passwords
            .build();
    userRepository.save(user);
}

// Login — compare without decoding (impossible to decode BCrypt)
public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail()).orElseThrow(...);

    // passwordEncoder.matches() re-hashes the submitted password and compares
    // It does NOT decrypt the stored hash (BCrypt is one-way)
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        handleFailedLogin(user);
        throw new BankingException("Invalid credentials", ...);
    }
    // login success
}
```

**Why BCrypt specifically?**
- **Adaptive:** The "cost factor" (10) can be increased as CPUs get faster
- **Salted:** Each hash includes a random salt — two identical passwords produce different hashes
- **Slow by design:** Takes ~100ms per hash, making brute-force attacks impractical

---

## Account Lockout Logic

```java
private static final int MAX_LOGIN_ATTEMPTS = 5;

private void handleFailedLogin(User user) {
    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

    if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
        user.setStatus(User.UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        log.warn("Account LOCKED due to {} failed attempts: {}",
                MAX_LOGIN_ATTEMPTS, user.getEmail());
        // Optionally: send security alert email
    }
    userRepository.save(user);
}

// On each login attempt, check if locked first:
if (user.getStatus() == User.UserStatus.LOCKED) {
    if (user.getLockedUntil() != null
            && LocalDateTime.now().isBefore(user.getLockedUntil())) {
        throw new BankingException(
            "Account locked. Try again later.",
            "ACCOUNT_LOCKED",
            HttpStatus.FORBIDDEN
        );
    }
    // Lock expired — reset
    user.setStatus(User.UserStatus.ACTIVE);
    user.setFailedLoginAttempts(0);
}
```

---

## CORS Configuration

**Configured in shared/security-lib/CorsConfig.java:**

```java
@Configuration
public class CorsConfig {

    @Value("${banking.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Which websites can call our API
        configuration.setAllowedOrigins(allowedOrigins);
        // ["http://localhost:5173", "https://app.bankingapp.com"]

        // Which HTTP methods are allowed
        configuration.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        // Which headers the browser can send
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Correlation-ID",
            "X-Request-ID"
        ));

        // Which headers the browser can read from the response
        configuration.setExposedHeaders(
            Arrays.asList("Authorization", "X-Correlation-ID")
        );

        // Allow cookies and Authorization header (required for JWT)
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        // (browser sends OPTIONS "preflight" request before actual request)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

## Token Blacklist (Logout)

When a user logs out, the access token is still valid for up to 15 minutes. We store it in Redis to block it:

```java
private static final String BLACKLIST_PREFIX = "blacklist:";

// On logout:
public void logout(String accessToken, String userId) {
    // Store in Redis with TTL = access token expiry
    // Key: "blacklist:eyJhbGci..."
    // Value: userId (who blacklisted it)
    // TTL: 1 hour (longer than any access token would be valid)
    redisTemplate.opsForValue().set(
        BLACKLIST_PREFIX + accessToken,
        userId,
        1,
        TimeUnit.HOURS
    );
}

// In API Gateway's AuthenticationFilter, check blacklist:
String blacklistKey = BLACKLIST_PREFIX + token;
if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
    // Token was explicitly invalidated — reject
    return unauthorized(exchange);
}
```
