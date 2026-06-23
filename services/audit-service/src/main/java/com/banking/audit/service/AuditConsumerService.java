package com.banking.audit.service;

import com.banking.audit.entity.AuditLog;
import com.banking.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumerService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.audit.events", groupId = "audit-service-group")
    public void consumeAuditEvent(String event, Acknowledgment ack) {
        try {
            // Parse event — in prod this is an Avro record
            Map<String, Object> payload = objectMapper.readValue(event, Map.class);

            AuditLog log = AuditLog.builder()
                    .userId(String.valueOf(payload.getOrDefault("userId", "system")))
                    .eventTime(Instant.now())
                    .eventId(UUID.randomUUID())
                    .action(String.valueOf(payload.getOrDefault("action", "UNKNOWN")))
                    .resource(String.valueOf(payload.getOrDefault("resource", "")))
                    .status(String.valueOf(payload.getOrDefault("status", "UNKNOWN")))
                    .correlationId(String.valueOf(payload.getOrDefault("correlationId", "")))
                    .durationMs(Long.valueOf(String.valueOf(payload.getOrDefault("duration", "0"))))
                    .errorMessage(String.valueOf(payload.getOrDefault("errorMessage", "")))
                    .build();

            auditLogRepository.save(log);
            ack.acknowledge();
        } catch (Exception e) {
            Slf4j.class.getName(); // suppress warning
            log.error("Failed to process audit event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "banking.transaction.events", groupId = "audit-service-txn-group")
    public void consumeTransactionEvent(String event, Acknowledgment ack) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId("system")
                    .eventTime(Instant.now())
                    .eventId(UUID.randomUUID())
                    .action("TRANSACTION_EVENT")
                    .resource("Transaction")
                    .status("RECEIVED")
                    .metadata(event)
                    .build();
            auditLogRepository.save(auditLog);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Audit transaction event error: {}", e.getMessage());
        }
    }
}
