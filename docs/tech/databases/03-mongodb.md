# MongoDB

## What Is MongoDB?

MongoDB is a NoSQL document database. Instead of rows in tables, it stores JSON-like documents in collections.

**Simple explanation:** PostgreSQL is like a spreadsheet — fixed columns, every row has the same structure. MongoDB is like a folder of sticky notes — each note can have different information on it.

---

## Why MongoDB for Notifications?

Notifications can have very different shapes:

```json
// Transaction notification
{
  "userId": "user-123",
  "title": "Money Received",
  "message": "John Doe sent you $500",
  "type": "TRANSACTION",
  "amount": 500,
  "currency": "USD",
  "senderName": "John Doe",
  "accountId": "acc-456"
}

// Security notification
{
  "userId": "user-123",
  "title": "New Login Detected",
  "message": "Login from Lagos, Nigeria",
  "type": "SECURITY",
  "ipAddress": "41.58.1.1",
  "city": "Lagos",
  "country": "Nigeria",
  "deviceType": "Android",
  "actionRequired": true,
  "actionUrl": "/security/review-login"
}

// Loan reminder
{
  "userId": "user-123",
  "title": "EMI Due in 5 Days",
  "message": "Your loan payment of $320.34 is due Feb 1",
  "type": "LOAN",
  "loanId": "loan-789",
  "emiAmount": 320.34,
  "dueDate": "2024-02-01",
  "penaltyIfMissed": 64.07
}
```

In PostgreSQL, you'd need either:
- One table with 20 columns, most NULL for each notification type (wasteful)
- Multiple tables joined together (complex queries)
- A JSONB column for the extra data (gives up type safety)

In MongoDB, each document is exactly what it needs to be. No wasted space, no NULL columns, no schema migrations when you add a new notification type.

---

## Our Notification Entity (MongoDB)

```java
// notification-service — Notification.java
@Document(collection = "notifications")  // = @Entity for MongoDB
                                          // 'collection' = table equivalent
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id                           // MongoDB generates this as ObjectId ("6579a1b2c3d4e5f6a7b8c9d0")
    private String id;

    private String userId;        // who this notification belongs to
    private String title;
    private String message;

    @Enumerated(EnumType.STRING)  // store enum name as string in MongoDB
    private NotificationType type;

    private NotificationChannel channel;  // EMAIL, SMS, PUSH, IN_APP
    private boolean read;
    private String referenceId;   // the transaction/payment/loan ID this is about
    private String referenceType; // "Transaction", "Payment", "Loan"

    @CreatedDate                  // Spring Data MongoDB auto-populates
    private LocalDateTime createdAt;

    // No @Table, no @Column — MongoDB is schemaless
    // Each document can have additional fields if needed

    public enum NotificationType { TRANSACTION, PAYMENT, ACCOUNT, SECURITY, MARKETING, SYSTEM }
    public enum NotificationChannel { EMAIL, SMS, PUSH, IN_APP }
}
```

---

## MongoDB Repository

```java
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    // Spring Data generates queries for MongoDB from method names
    // Just like JpaRepository but for MongoDB

    // db.notifications.find({userId: "user-123"}).sort({createdAt: -1}).skip(N).limit(M)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // db.notifications.find({userId: "user-123", read: false})
    List<Notification> findByUserIdAndReadFalse(String userId);

    // db.notifications.countDocuments({userId: "user-123", read: false})
    long countByUserIdAndReadFalse(String userId);

    // Custom MongoDB query
    @Query("{ 'userId': ?0, 'type': ?1, 'createdAt': { $gte: ?2 } }")
    // ?0, ?1, ?2 = method parameters in order
    List<Notification> findByUserIdAndTypeAfter(
            String userId, NotificationType type, LocalDateTime after);
}
```

---

## MongoDB Indexing

Without indexes, MongoDB scans every document in the collection (full collection scan). With the right indexes, queries run in O(log n) time.

```java
// Add indexes on the entity class:
@Document(collection = "notifications")
@CompoundIndex(def = "{'userId': 1, 'createdAt': -1}", name = "idx_user_date")
// 1 = ascending, -1 = descending
// This compound index optimizes: find by userId, sort by date newest first

@CompoundIndex(def = "{'userId': 1, 'read': 1}", name = "idx_user_unread")
// Optimizes: find unread notifications for a user
public class Notification { ... }
```

Or via Spring Data auto-index:
```yaml
spring.data.mongodb.auto-index-creation: true
# Automatically creates indexes declared on @Document classes
```

---

## MongoDB Configuration

```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb://banking:banking-secret@localhost:27017/banking_notifications?authSource=admin
      # Format: mongodb://username:password@host:port/database?authSource=admin
      # authSource: the database that contains the user account
      database: banking_notifications
      auto-index-creation: true
```

**Connection pool (auto-configured):**
MongoDB Java driver maintains a connection pool by default:
- Default pool size: 100 connections per host
- Connections are reused across requests
- No additional configuration needed for basic usage
