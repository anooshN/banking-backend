# Module 6 — Functional Programming (Java 8+)

---

## 1. Lambda Expressions

**Situation:**
Throughout the project we need to pass behavior — what to do when an exception isn't found, how to filter notifications, how to transform roles. Without lambdas, this requires anonymous inner classes — verbose boilerplate.

**Task:**
Use lambda expressions to pass behavior concisely, making code readable and expressive.

**Action:**

```java
// LAMBDA SYNTAX: (parameters) -> expression OR (parameters) -> { statements }

// services/auth-service — AuthService.java
// Lambda as orElseThrow argument — provides the exception to throw
User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new BankingException(
                "Invalid credentials",
                "INVALID_CREDENTIALS",
                HttpStatus.UNAUTHORIZED
        ));
// () -> means: no parameters, returns a new BankingException
// Without lambda: would need an anonymous Supplier<BankingException> class

// shared/security-lib — JwtAuthenticationFilter.java
// Lambda for stream operations
List<SimpleGrantedAuthority> authorities = roles.stream()
        .map(role -> new SimpleGrantedAuthority(role))  // role -> expression
        // Or method reference: .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());

// services/fraud-detection-service — FraudEvaluationService.java
// Lambda with conditional — fraud rule evaluation
boolean isHighVelocity = Optional.ofNullable(txnCount)
        .map(count -> count > MAX_TRANSACTIONS_PER_HOUR)  // count -> boolean
        .orElse(false);

// shared/exception-lib — GlobalExceptionHandler.java
// Lambda in forEach — collect validation errors
ex.getBindingResult().getAllErrors().forEach(error -> {
    // Multi-statement lambda — block body with { }
    String fieldName = ((FieldError) error).getField();
    String errorMessage = error.getDefaultMessage();
    errors.put(fieldName, errorMessage);
});

// services/payment-service — PaymentService.java
// Lambda in @Scheduled — conceptual behavior
// The @Scheduled annotation itself IS a callback — Spring calls processOutbox()
// Every 5 seconds as if: scheduler.schedule(() -> processOutbox(), 5, SECONDS)

// services/notification-service — NotificationService.java
// Lambda for marking all read
List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
unread.forEach(notification -> notification.setRead(true));  // n -> sideEffect
notificationRepository.saveAll(unread);
```

---

## 2. Stream API

**Situation:**
We need to transform role strings to authority objects, filter active accounts, calculate total balance, find accounts by criteria — all on collections of data.

**Task:**
Use Stream API for declarative, functional collection processing — eliminating manual loops.

**Action:**

```java
// shared/security-lib — JwtAuthenticationFilter.java
// MAP: transform each role string to a GrantedAuthority object
List<SimpleGrantedAuthority> authorities = roles.stream()
        .map(role -> new SimpleGrantedAuthority(role))
        // map: String → SimpleGrantedAuthority
        .collect(Collectors.toList());

// services/account-service — AccountService.java
// FILTER: only active accounts
List<Account> activeAccounts = accounts.stream()
        .filter(account -> account.getStatus() == Account.AccountStatus.ACTIVE)
        // filter: keep only those where predicate returns true
        .collect(Collectors.toList());

// REDUCE: total balance across all accounts
BigDecimal totalBalance = accounts.stream()
        .map(Account::getBalance)              // Account → BigDecimal
        .reduce(BigDecimal.ZERO, BigDecimal::add);  // accumulate: 0 + bal1 + bal2 + ...
// Or: .mapToDouble(a -> a.getBalance().doubleValue()).sum()
// But BigDecimal::add is safer for money (no floating point)

// services/fraud-detection-service — FraudEvaluationService.java
// SUM: total fraud score from multiple rules
double totalScore = fraudRules.stream()
        .filter(rule -> rule.isApplicable(transaction))
        .mapToDouble(FraudRule::getScore)  // primitive double stream
        .sum();

// ANYMATCH: check if any account is FROZEN
boolean hasAnyFrozen = accounts.stream()
        .anyMatch(a -> a.getStatus() == Account.AccountStatus.FROZEN);

// ALLMATCH: check if all accounts are ACTIVE (for a batch operation)
boolean allActive = accounts.stream()
        .allMatch(a -> a.getStatus() == Account.AccountStatus.ACTIVE);

// FINDMATCH: get first active account
Optional<Account> primaryAccount = accounts.stream()
        .filter(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
        .findFirst();

// COUNT:
long activeCount = accounts.stream()
        .filter(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
        .count();

// COLLECT to Map — group transactions by type:
Map<Transaction.TransactionType, List<Transaction>> byType = transactions.stream()
        .collect(Collectors.groupingBy(Transaction::getTransactionType));
// Result: {DEBIT: [txn1, txn3], CREDIT: [txn2, txn4], TRANSFER_OUT: [txn5]}

// COLLECT joining — build comma-separated string of roles for logging:
String rolesStr = roles.stream()
        .collect(Collectors.joining(", "));
// "ROLE_CUSTOMER, ROLE_TELLER"

// SORTED: transactions newest first
List<Transaction> sorted = transactions.stream()
        .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
        .collect(Collectors.toList());

// DISTINCT: remove duplicate account IDs:
List<UUID> uniqueAccountIds = transactions.stream()
        .map(Transaction::getAccountId)
        .distinct()
        .collect(Collectors.toList());

// FLATMAP: flatten list of lists (each user has multiple accounts):
List<Account> allAccounts = users.stream()
        .flatMap(user -> accountRepository.findByUserId(user.getId()).stream())
        // map would give Stream<List<Account>> — flatMap flattens to Stream<Account>
        .collect(Collectors.toList());
```

---

## 3. Optional

**Situation:**
Every repository `findBy` method can return null (user not found, account not found). Without Optional, we'd have `if (result != null)` checks everywhere, and NullPointerExceptions when we forget them.

**Task:**
Use Optional to make the possibility of absence explicit — forcing callers to handle the "not found" case.

**Action:**

```java
// DEFINITION: Optional<T> = a container that may or may not hold a value
// Repository methods return Optional<T>:
Optional<User> findByEmail(String email);
Optional<Account> findById(UUID id);

// PATTERN 1: orElseThrow — the most common pattern
// services/auth-service — AuthService.java
User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new BankingException(
                "Invalid credentials",
                "INVALID_CREDENTIALS",
                HttpStatus.UNAUTHORIZED
        ));
// If empty: throws BankingException immediately
// If present: returns the User

// PATTERN 2: orElse — provide default
String currency = Optional.ofNullable(request.getCurrency())
        .orElse("USD");  // if null, use "USD"
// Cleaner than: String currency = request.getCurrency() != null ? request.getCurrency() : "USD";

// PATTERN 3: map — transform if present
Optional<String> maskedEmail = userRepository.findByEmail(email)
        .map(user -> user.getEmail().replaceAll("(.).*(@.*)", "$1***$2"));
// If user found: transforms email to "j***@bank.com"
// If not found: empty Optional (no NullPointerException)

// PATTERN 4: ifPresent — execute if present
userRepository.findByEmail(email).ifPresent(user -> {
    // Only runs if user exists
    eventProducer.publishEvent(TOPIC_USER_EVENTS, user.getId().toString(), "USER_ACCESSED");
});

// PATTERN 5: filter — apply condition to Optional value
Optional<Account> frozenAccount = accountRepository.findById(accountId)
        .filter(account -> account.getStatus() == Account.AccountStatus.FROZEN);
// If present AND FROZEN: returns the Optional<Account>
// If present but not FROZEN: returns empty Optional
// If not found: returns empty Optional

// PATTERN 6: isPresent / isEmpty
Optional<User> userOpt = userRepository.findByEmail(email);
if (userOpt.isPresent()) {
    User user = userOpt.get();  // safe because we checked isPresent()
    // process user
}
// Better:
userOpt.ifPresent(user -> processUser(user));

// ANTI-PATTERN — don't do this:
User user = userRepository.findByEmail(email).get(); // THROWS NoSuchElementException if empty
// Never call .get() without checking isPresent() first
// Use orElseThrow() instead

// Optional.ofNullable — wrap a potentially null value:
String mfaCode = Optional.ofNullable(request.getMfaCode())
        .filter(code -> !code.isBlank())  // treat blank as absent
        .orElse(null);
```

---

## 4. Method References

**Situation:**
When a lambda just calls an existing method, method references make the code more readable — replacing `x -> SomeClass.method(x)` with `SomeClass::method`.

**Task:**
Use method references to express intent more clearly when a lambda is just a method call.

**Action:**

```java
// TYPES of method references:

// 1. Static method reference: ClassName::staticMethod
// services/auth-service — converting roles
List<String> roles = List.of("ROLE_CUSTOMER", "ROLE_ADMIN");
// Lambda: roles.stream().map(role -> String.valueOf(role))
// Method reference:
roles.stream().map(String::valueOf);  // same thing, cleaner

// 2. Instance method reference on specific object: instance::method
BankingEventProducer producer = this.eventProducer;
// Lambda: message -> producer.publishEvent(topic, key, message)
// Method reference (when object is already bound):
// messages.forEach(producer::publishEvent) — less applicable here

// 3. Instance method reference on arbitrary object: ClassName::instanceMethod
List<Account> accounts = accountRepository.findByUserId(userId);
// Lambda: accounts.stream().map(account -> account.getAccountNumber())
// Method reference:
accounts.stream().map(Account::getAccountNumber);  // cleaner

// Lambda: accounts.stream().map(account -> account.getBalance())
accounts.stream().map(Account::getBalance);

// 4. Constructor reference: ClassName::new
// shared/security-lib — JwtAuthenticationFilter.java
List<SimpleGrantedAuthority> authorities = roles.stream()
        .map(SimpleGrantedAuthority::new)  // = role -> new SimpleGrantedAuthority(role)
        .collect(Collectors.toList());

// services/auth-service — building User from request
// Lambda: () -> new BankingException("msg", "code", status)
// Can use constructor reference if args match:
// Supplier<BankingException> supplier = BankingException::new;

// In practice — method references throughout:
// .map(Account::getBalance) instead of .map(a -> a.getBalance())
// .filter(Objects::nonNull) instead of .filter(o -> o != null)
// .collect(Collectors.joining(", "))
// .forEach(System.out::println) — during debugging
// .sorted(Comparator.comparing(Transaction::getCreatedAt))
```

---

## 5. Functional Interfaces

**Situation:**
Java 8 introduced functional interfaces — interfaces with exactly one abstract method. Lambdas implement them. Understanding the built-in ones helps recognize what type a lambda parameter expects.

**Task:**
Recognize and use the standard functional interfaces from `java.util.function`.

**Action:**

```java
// java.util.function — built-in functional interfaces:

// Supplier<T>: () → T (no input, produces output)
// Used in orElseThrow:
Supplier<BankingException> exceptionSupplier =
        () -> new BankingException("Not found", "NOT_FOUND", HttpStatus.NOT_FOUND);
account = accountRepository.findById(id).orElseThrow(exceptionSupplier);

// Predicate<T>: T → boolean (test a condition)
// Used in stream filter:
Predicate<Account> isActive = account -> account.getStatus() == Account.AccountStatus.ACTIVE;
Predicate<Account> hasBalance = account -> account.getBalance().compareTo(BigDecimal.ZERO) > 0;
// Compose predicates:
Predicate<Account> isActiveWithBalance = isActive.and(hasBalance);
List<Account> eligible = accounts.stream().filter(isActiveWithBalance).collect(Collectors.toList());

// Function<T, R>: T → R (transform input to output)
// Used in stream map:
Function<Account, String> toAccountNumber = Account::getAccountNumber;
Function<Account, BigDecimal> toBalance = Account::getBalance;
Function<Account, String> toMasked = account ->
        "**** " + account.getAccountNumber().substring(account.getAccountNumber().length() - 4);
// Compose functions:
Function<String, String> upperCase = String::toUpperCase;
Function<Account, String> toUpperAccountNumber = toAccountNumber.andThen(upperCase);

// Consumer<T>: T → void (consume without returning)
// Used in forEach, ifPresent:
Consumer<Notification> markRead = notification -> notification.setRead(true);
notifications.forEach(markRead);

// BiFunction<T, U, R>: T, U → R (two inputs)
// Used in Kafka whenComplete callback:
kafkaTemplate.send(topic, key, event).whenComplete(
        (result, ex) -> { ... }  // BiFunction-like (result, exception) → void
);

// UnaryOperator<T>: T → T (same type in and out)
// Useful for transformations like masking:
UnaryOperator<String> maskEmail = email ->
        email.replaceAll("(.).*(@.*)", "$1***$2");

// BinaryOperator<T>: T, T → T (combine two same-type values)
// Used in reduce:
BigDecimal totalBalance = accounts.stream()
        .map(Account::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add);  // BigDecimal::add is a BinaryOperator
```

**Interview Questions:**

- Q: What is a functional interface? Can you give examples from your project?
  A: "A functional interface has exactly one abstract method. In our banking project, we use them extensively with lambdas. `Supplier<BankingException>` is `() -> new BankingException(...)` — no input, produces an exception. `Predicate<Account>` is `account -> account.getStatus() == ACTIVE` — takes an Account, returns boolean, used in stream.filter(). `Function<Account, String>` is `Account::getAccountNumber` — takes Account, returns String, used in stream.map(). The key: anywhere we pass a lambda in our code, we're implementing a functional interface."

- Q: What is the difference between map() and flatMap()?
  A: "In our banking project, if each user has a list of accounts, `users.stream().map(user -> accountRepository.findByUserId(user.getId()))` gives `Stream<List<Account>>` — a stream of lists. To work with individual accounts, we need `flatMap` which flattens: `users.stream().flatMap(user -> accountRepository.findByUserId(user.getId()).stream())` gives `Stream<Account>`. FlatMap = map + flatten. We use it when the mapping function itself returns a collection and we want a flat stream of elements."
