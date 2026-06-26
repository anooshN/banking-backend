# Spring Kafka

## What Is Spring Kafka?

Spring Kafka is a wrapper around the Apache Kafka Java client that makes it idiomatic to use in Spring Boot applications — with annotations, auto-configuration, and integration with Spring's transaction management.

---

## Producer Configuration

```java
// shared/kafka-lib — KafkaProducerConfig.java
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;  // "localhost:9092" or MSK broker list

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl; // "http://localhost:8085"

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Where to find Kafka brokers
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Key = String (we use transaction ID, account ID, etc. as keys)
        // Key determines which partition the message goes to
        // Same key = same partition = ordering guaranteed for that key
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Value = Avro serialized object
        // Avro schema is looked up/registered in Schema Registry
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        // ── Exactly-Once Semantics ──────────────────────────────────────────
        // Without idempotence: network retry can cause duplicate messages
        // With idempotence: Kafka deduplicates retries automatically
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // acks=all: wait for ALL in-sync replicas to confirm before returning
        // (not just the leader broker)
        // Slowest but safest — message cannot be lost even if leader dies
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retry indefinitely — combined with idempotence this is safe
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        // Max 5 requests in-flight per connection
        // Required for idempotence to maintain ordering
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // ── Performance Tuning ─────────────────────────────────────────────
        // Compress messages before sending (Snappy is fast with good compression)
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Wait up to 5ms to accumulate messages into a batch before sending
        // Tradeoff: 5ms extra latency → much better throughput
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Max batch size in bytes (32KB)
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

---

## BankingEventProducer — Async Publishing

```java
// shared/kafka-lib — BankingEventProducer.java
@Slf4j
@Component
@RequiredArgsConstructor
public class BankingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Fire-and-forget: publishes and returns immediately
    // Callback logs success/failure
    public void publishEvent(String topic, String key, Object event) {
        // kafkaTemplate.send() is non-blocking — returns a CompletableFuture
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success: log partition and offset for traceability
                log.info("Published event to topic: {} partition: {} offset: {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // Failure: log error (with exactly-once + retries, this rarely happens)
                log.error("FAILED to publish event to topic: {} key: {} error: {}",
                        topic, key, ex.getMessage());
                // In production: alert ops team, trigger incident
            }
        });
        // Returns immediately — HTTP request doesn't wait for Kafka
    }

    // When you NEED to wait for confirmation (rare — slows down the caller)
    public CompletableFuture<SendResult<String, Object>> publishEventAsync(
            String topic, String key, Object event) {
        return kafkaTemplate.send(topic, key, event);
        // Caller can .get() to block, or .whenComplete() for callback
    }
}
```

---

## Consumer Configuration

```java
// shared/kafka-lib — KafkaConsumerConfig.java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Each service has a different group-id, e.g., "notification-service-group"
        // This means each service independently reads all messages

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        // specific.avro.reader=true: deserialize to the generated Avro Java class
        // (not a generic GenericRecord)

        // Manual acknowledgment — we control when to commit offset
        // auto-commit = false: don't move the offset until we explicitly ack
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // If no previous offset (new group): start from the beginning
        // "latest" would start from now (miss messages sent before consumer started)
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Only read messages from committed (completed) transactions
        // Prevents reading uncommitted messages from failed transactions
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
            kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory());

        // Manual acknowledgment mode
        factory.getContainerProperties()
               .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Run 3 consumer threads per container
        // With 6 partitions and 3 threads: each thread reads 2 partitions
        factory.setConcurrency(3);

        // Error handler: retry 3 times with 1 second between retries
        // After 3 failures: message goes to Dead Letter Queue
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate), // send to DLQ
                new FixedBackOff(1000L, 3)                       // 3 retries, 1s apart
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
```

---

## @KafkaListener — Consuming Messages

```java
// notification-service — NotificationService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket
    private final EmailService emailService;

    @KafkaListener(
        topics = "banking.transaction.events",
        groupId = "notification-service-group"  // overrides the default group if needed
    )
    public void handleTransactionEvent(String event, Acknowledgment ack) {
        // Acknowledgment is injected by Spring Kafka (manual ack mode)
        try {
            log.info("Received transaction event: {}", event);

            // Parse the event (in production: Avro deserialization)
            // Here simplified as String parsing
            // Real: TransactionEvent avroEvent = (TransactionEvent) event;

            // Create notification in MongoDB
            Notification notification = createNotification(
                    extractUserId(event),
                    "Transaction Alert",
                    formatMessage(event),
                    Notification.NotificationType.TRANSACTION,
                    extractTransactionId(event)
            );

            // ack AFTER successful processing
            // If we ack before processing and then crash, event is lost
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing transaction event: {}", e.getMessage(), e);
            // DON'T call ack.acknowledge()
            // Kafka will redeliver this message (up to retry limit)
            // After 3 retries, goes to DLQ
        }
    }

    // Listen to multiple topics with one method
    @KafkaListener(topics = {"banking.payment.events", "banking.account.events"})
    public void handleMultipleEvents(String event, Acknowledgment ack,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        // @Header injects Kafka metadata
        try {
            log.info("Received event from topic: {}", topic);
            if (topic.contains("payment")) {
                // handle payment event
            } else if (topic.contains("account")) {
                // handle account event
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing event from {}: {}", topic, e.getMessage());
        }
    }
}
```

---

## Kafka Headers and Metadata

```java
// Access Kafka message metadata in the listener:
@KafkaListener(topics = "banking.transaction.events")
public void consume(
        String event,
        Acknowledgment ack,
        @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,      // topic name
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,     // which partition
        @Header(KafkaHeaders.OFFSET)             long offset,       // message offset
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {  // when produced

    log.info("Message from {}:{} offset:{} timestamp:{}",
            topic, partition, offset, timestamp);
    ack.acknowledge();
}
```

---

## Kafka Topics in Our App

| Topic | Producer | Consumers | Key |
|---|---|---|---|
| banking.transaction.events | transaction-service | notification, audit, fraud | transaction-id |
| banking.payment.events | payment-service | transaction, notification, audit, fraud | payment-id |
| banking.account.events | account-service | notification, audit | account-id |
| banking.fraud.events | fraud-detection-service | notification, account | transaction-id |
| banking.notification.events | various | notification-service | user-id |
| banking.audit.events | @Auditable AOP | audit-service | user-id |
| banking.user.events | auth-service, user-service | notification, audit | user-id |

**Why use accountId/userId as the key?**

All messages with the same key go to the same partition. This guarantees ordering for that key:
- All events for account-123 go to partition 2
- Debit event before credit event → they're processed in that order
- No ordering issues, no race conditions for a single account
