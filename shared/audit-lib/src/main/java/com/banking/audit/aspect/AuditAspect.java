package com.banking.audit.aspect;

import com.banking.audit.annotation.Auditable;
import com.banking.common.constants.BankingConstants;
import com.banking.kafka.producer.BankingEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final BankingEventProducer eventProducer;

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String userId = extractUserId();
        String correlationId = MDC.get("correlationId");
        long startTime = System.currentTimeMillis();
        Object result = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILURE";
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            publishAuditEvent(auditable, userId, correlationId, status, errorMessage, duration);
        }
    }

    private void publishAuditEvent(Auditable auditable, String userId, String correlationId,
                                    String status, String errorMessage, long duration) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("action", auditable.action());
            auditEvent.put("resource", auditable.resource());
            auditEvent.put("userId", userId);
            auditEvent.put("correlationId", correlationId);
            auditEvent.put("status", status);
            auditEvent.put("errorMessage", errorMessage);
            auditEvent.put("duration", duration);
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            eventProducer.publishEvent(BankingConstants.TOPIC_AUDIT_EVENTS, userId, auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", e.getMessage());
        }
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
