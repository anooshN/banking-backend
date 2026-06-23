# 6. Kafka — The Message Bus

## The Simple Version

Imagine a school announcement system. The principal speaks into a microphone (produces a message). Every classroom hears it at the same time (multiple consumers). The announcement is also recorded, so if a student was absent, they can hear the recording later (message retention).

Apache Kafka is that system, but for software services.

---

## Core Concepts

### Topic
A **topic** is a named channel. Like different TV channels — channel 1 for news, channel 2 for sports.

Our banking app has these topics:
```
banking.account.events       ← account changes
banking.transaction.events   ← money movements
banking.payment.events       ← wire transfers, ACH
banking.fraud.events         ← fraud detections
banking.notification.events  ← notifications to send
banking.audit.events         ← audit log entries
banking.user.events          ← user profile changes
```

### Producer
A service that **sends** messages to a topic. Like the principal speaking into the microphone.

### Consumer
A service that **reads** messages from a topic. Like the classrooms hearing the announcement.

### Partition
Each topic is split into **partitions**. Think of it like having 6 microphones in 6 different rooms — messages can flow through all of them in parallel.

More partitions = higher throughput (can process more messages per second).

Our topics have 6 partitions by default. This means 6 consumers can read from one topic simultaneously.

### Consumer Group
When multiple instances of the same service are running (for scale), they form a **consumer group**. Kafka ensures each message is processed by only ONE instance in the group — not all of them.

Simple explanation: If there are 3 notification-service instances and 1 new notification arrives, only 1 of the 3 instances processes it. Otherwise you'd send 3 emails instead of 1.

### Offset
Kafka remembers where each consumer group stopped reading — this is the **offset**. Like a bookmark in a book.

If notification-service crashes and restarts, it resumes from its bookmark. No messages are lost or duplicated.

---

## Why Kafka Instead of RabbitMQ or a Simple Queue?

| Feature | Kafka | Simple Queue (RabbitMQ) |
|---|---|---|
| Message retention | Keeps messages for days/weeks | Deletes after consumed |
| Multiple consumers | Many consumer groups, independent | Message goes to ONE consumer |
| Ordering | Guaranteed per partition | Not guaranteed |
| Throughput | Millions of messages/second | Thousands/second |
| Replay | Can re-read old messages | Cannot replay |
| Scalability | Horizontal scaling built-in | Harder to scale |

For banking, the key advantage is **replay**. If the fraud detection service has a bug and misses some fraud events, you can fix the bug and re-process the last week of messages. With a simple queue, those messages are gone forever.

---

## Exactly-Once Semantics

**Problem:** Normally, messaging systems guarantee "at least once delivery" — the message might be delivered more than once. If a transaction event is delivered twice, the notification service sends two SMS messages. Annoying but not dangerous.

But what if the account balance update is delivered twice? You credit $500 twice. That IS dangerous.

**Solution:** Kafka supports **exactly-once semantics** with idempotent producers.

Our Kafka producer config:
```java
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // exactly-once
config.put(ProducerConfig.ACKS_CONFIG, "all");               // wait for all replicas
config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);// retry until success
```

`ENABLE_IDEMPOTENCE_CONFIG = true` means: even if the producer retries (due to a network glitch), Kafka detects the duplicate and discards it. The message is processed exactly once.

---

## Manual Acknowledgment

Our consumers use manual acknowledgment:
```java
@KafkaListener(topics = "banking.transaction.events")
public void handleTransaction(String event, Acknowledgment ack) {
    try {
        processEvent(event);
        ack.acknowledge(); // ← tell Kafka: I processed this successfully
    } catch (Exception e) {
        // Don't acknowledge → Kafka will redeliver this message
        log.error("Failed to process, will retry");
    }
}
```

If the consumer crashes before calling `ack.acknowledge()`, Kafka redelivers the message when the consumer comes back. This guarantees no messages are silently lost.

---

## Dead Letter Queue (DLQ)

What if a message keeps failing? Maybe it has corrupt data that will always fail processing.

After 3 retries, failed messages are sent to a DLQ topic: `banking.transaction.events.dlq`

Operations staff monitor the DLQ. They can:
1. Investigate why the message failed
2. Fix the bug
3. Replay the message from the DLQ back to the main topic

---

## The Outbox Pattern (Transaction Guarantee)

**The problem:**
```
1. Save payment to database  ✓
2. ← CRASH HERE
3. Publish to Kafka          ✗ NEVER HAPPENS
```

The payment is saved but the event is never published. Other services never find out. Money appears to vanish.

**The solution:**
```
1. Save payment to database (outbox_processed = false)  ✓
2. ← CRASH HERE (recovery picks up from step 3)
3. Scheduler polls: "any unprocessed payments?"         ✓
4. Publish to Kafka                                     ✓
5. Mark outbox_processed = true                         ✓
```

The database save and the "intent to publish" are atomic — they are committed together in one database transaction. The actual Kafka publish happens separately but is guaranteed to eventually happen.

---

## Schema Registry (Avro)

**Problem:** Producer sends: `{"amount": 500, "currency": "USD"}`
Consumer reads it and expects `{"amount": 500, "currencyCode": "USD"}`

Field name changed → consumer crashes.

**Solution:** All messages use **Avro schemas** registered in Confluent Schema Registry.

Before a producer can publish, the schema is validated. If a consumer tries to read a message with an incompatible schema, it fails at startup, not silently at runtime.

This is like agreeing on a contract before sending data:
- Producer must send messages matching schema version X
- Consumer declares it can read schema version X
- Registry enforces the contract

Our Kafka topics: topics with `banking.` prefix use Avro schemas registered at `http://schema-registry:8085`.
