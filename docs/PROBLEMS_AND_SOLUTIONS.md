# Problems Faced & How We Resolved Them

Every error we hit during local setup and exactly how we fixed it.

---

## Problem 1 — Wrong Java Version (Maven using Java 25 instead of Java 17)

**Error:**
```
Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

**Cause:** The project requires Java 17 but Java 25 was the default on the machine.

**Fix:** Install Java 17 (Temurin) from https://adoptium.net and set JAVA_HOME before every Maven command:
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
```

---

## Problem 2 — Lombok/MapStruct Version Missing in Root POM

**Error:**
```
Resolution of annotationProcessorPath dependencies failed: version can neither be null, empty nor blank
```

**Cause:** The root `pom.xml` had Lombok in `annotationProcessorPaths` without a `<version>` tag.

**Fix:** Added `<version>1.18.30</version>` to the Lombok annotationProcessorPath in root `pom.xml`:
```xml
<path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
</path>
```

---

## Problem 3 — spring-data-commons Missing in common-utils

**Error:**
```
package org.springframework.data.domain does not exist
```

**Cause:** `PageResponse.java` uses `org.springframework.data.domain.Page` but the dependency was missing.

**Fix:** Added to `shared/common-utils/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-commons</artifactId>
</dependency>
```

---

## Problem 4 — JJWT API Changed (parserBuilder removed)

**Error:**
```
cannot find symbol: method parserBuilder() in class io.jsonwebtoken.Jwts
```

**Cause:** The newer JJWT version removed `parserBuilder()` in favor of `parser()`.

**Fix:** Rewrote `JwtTokenProvider.java` to use new API:
```java
// Old (broken):
Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)

// New (fixed):
Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
```

---

## Problem 5 — Spring Security Missing in audit-lib and exception-lib

**Error:**
```
package org.springframework.security.core does not exist
```

**Cause:** `AuditAspect.java` and `GlobalExceptionHandler.java` use Spring Security classes but the dependency was missing from those modules.

**Fix:** Added to both `shared/audit-lib/pom.xml` and `shared/exception-lib/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

---

## Problem 6 — Testcontainers JUnit Jupiter Missing

**Error:**
```
package org.testcontainers.junit.jupiter does not exist
```

**Cause:** Test files used `@Testcontainers` and `@Container` annotations but `junit-jupiter` artifact was missing.

**Fix:** Added to all service pom.xml files:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Problem 7 — PostgreSQL Port 5432 Already Used by Windows

**Error:**
```
postgres-auth container shows 5432/tcp (no host mapping)
```

**Cause:** Windows had PostgreSQL installed natively on port 5432, blocking Docker from binding to it.

**Fix:** Recreated the postgres-auth container with port 5436:
```cmd
docker run -d --name banking-backend-postgres-auth-1 \
  --network banking-backend_banking-network \
  -e POSTGRES_DB=banking_auth \
  -e POSTGRES_USER=banking \
  -e POSTGRES_PASSWORD=banking-secret \
  -p 5436:5432 \
  -v banking-backend_postgres-auth-data:/var/lib/postgresql/data \
  postgres:16-alpine
```

Then set the DB URL with port 5436:
```cmd
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5436/banking_auth
```

---

## Problem 8 — Flyway Unsupported Database PostgreSQL 16

**Error:**
```
Unsupported Database: PostgreSQL 16.14
```

**Cause:** Flyway 10.4.1 does not support PostgreSQL 16 without the extra `flyway-database-postgresql` artifact.

**Fix:** Upgraded Flyway and added the PostgreSQL plugin to all service pom.xml files:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>10.10.0</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <version>10.10.0</version>
</dependency>
```

---

## Problem 9 — PasswordEncoder Bean Not Found

**Error:**
```
No qualifying bean of type 'org.springframework.security.crypto.password.PasswordEncoder' available
```

**Cause:** Each service needs its own `SecurityConfig` that defines the `PasswordEncoder` bean.

**Fix:** Created `SecurityConfig.java` in every service:
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

---

## Problem 10 — JwtTokenProvider Bean Not Found

**Error:**
```
No qualifying bean of type 'com.banking.security.jwt.JwtTokenProvider' available
```

**Cause:** Spring Boot only scans the service's own package (e.g., `com.banking.auth`) by default. Shared library beans in `com.banking.security` were not found.

**Fix:** Added `@ComponentScan` to every Application class:
```java
@ComponentScan(basePackages = {
    "com.banking.auth",
    "com.banking.security",   // ← shared library
    "com.banking.common",
    "com.banking.kafka",
    "com.banking.audit",
    "com.banking.exception"
})
```

---

## Problem 11 — currency_code Column Type Mismatch

**Error:**
```
Schema-validation: wrong column type encountered in column [currency_code];
found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]
```

**Cause:** Flyway created the column as `CHAR(3)` (PostgreSQL type `bpchar`) but Hibernate expected `VARCHAR(3)`.

**Fix:** Altered the column type directly in each database:
```cmd
docker exec banking-backend-postgres-accounts-1 psql -U banking -d banking_accounts -c "ALTER TABLE accounts ALTER COLUMN currency_code TYPE VARCHAR(3);"
docker exec banking-backend-postgres-transactions-1 psql -U banking -d banking_transactions -c "ALTER TABLE transactions ALTER COLUMN currency_code TYPE VARCHAR(3);"
docker exec banking-backend-postgres-payments-1 psql -U banking -d banking_payments -c "ALTER TABLE payments ALTER COLUMN currency_code TYPE VARCHAR(3);"
```

---

## Problem 12 — Payment Service Port Conflict (8085 used by Schema Registry)

**Error:**
```
Web server failed to start. Port 8085 was already in use.
```

**Cause:** Schema Registry Docker container was already using port 8085 and payment-service also tried to use 8085.

**Fix:** Changed payment-service port to 8087 in `application.yml`:
```yaml
server:
  port: 8087
```

---

## Problem 13 — Flyway Checksum Mismatch

**Error:**
```
Migration checksum mismatch for migration version 1
-> Applied to database: -898706916
-> Resolved locally: -479063169
```

**Cause:** The migration SQL file was modified after it had already been applied to the database.

**Fix:** Deleted the old history record and re-inserted with the correct checksum:
```cmd
docker exec banking-backend-postgres-auth-1 psql -U banking -d banking_auth -c "DELETE FROM flyway_schema_history WHERE version='1';"

docker exec banking-backend-postgres-auth-1 psql -U banking -d banking_auth -c "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (1, '1', 'create users table', 'SQL', 'V1__create_users_table.sql', -479063169, 'banking', NOW(), 100, true);"
```

---

## Problem 14 — API Gateway: Spring MVC Incompatible with Spring Cloud Gateway

**Error:**
```
Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway.
```

**Cause:** Spring Cloud Gateway requires reactive (WebFlux) mode but Spring MVC was on the classpath.

**Fix:** Added to `api-gateway/application.yml`:
```yaml
spring:
  main:
    web-application-type: reactive
```

---

## Problem 15 — API Gateway: authenticationManager Cannot Be Null

**Error:**
```
authenticationManager cannot be null
```

**Cause:** Spring Security tried to auto-configure HTTP Basic auth in reactive mode, which needs an authentication manager.

**Fix:** Created a reactive `SecurityConfig` in the API Gateway:
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
        return http.build();
    }
}
```

---

## Problem 16 — User Entity created_at Is Null

**Error:**
```
null value in column "created_at" of relation "users" violates not-null constraint
```

**Cause:** `@CreatedDate` (JPA Auditing) was not working because `@EnableJpaAuditing` was missing from the Application class.

**Fix:** Added `@EnableJpaAuditing` to `AuthServiceApplication.java` AND added `@Builder.Default` to timestamp fields:
```java
@Builder.Default
@Column(name = "created_at", updatable = false)
private LocalDateTime createdAt = LocalDateTime.now();

@Builder.Default
@Column(name = "updated_at")
private LocalDateTime updatedAt = LocalDateTime.now();
```

---

## Problem 17 — createAccount Returns 403 Access Denied

**Error:**
```
{"success":false,"message":"Access denied","errorCode":"ACCESS_DENIED"}
```

**Cause:** The `createAccount` endpoint only allowed `ADMIN` and `TELLER` roles, not `CUSTOMER`.

**Fix:** Updated `AccountController.java`:
```java
// Before:
@PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")

// After:
@PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
```
