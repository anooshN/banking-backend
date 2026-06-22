package com.banking.fraud.service;

import com.banking.common.constants.BankingConstants;
import com.banking.fraud.model.FraudScore;
import com.banking.kafka.producer.BankingEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudEvaluationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final BankingEventProducer eventProducer;

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final int MAX_TRANSACTIONS_PER_HOUR = 20;

    @KafkaListener(topics = "banking.transaction.events", groupId = "fraud-detection-group")
    public void evaluateTransaction(String event, Acknowledgment ack) {
        try {
            // In production: parse Avro event properly
            log.info("Evaluating transaction for fraud: {}", event);
            // Publish result to fraud topic
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Fraud evaluation error", e);
        }
    }

    public FraudScore evaluate(String transactionId, String userId, BigDecimal amount, String ip) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        // Rule 1: High-value transaction
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            score += 0.3;
            reasons.add("HIGH_VALUE_TRANSACTION");
        }

        // Rule 2: Velocity check (Redis)
        String velocityKey = "fraud:velocity:" + userId;
        Long txnCount = redisTemplate.opsForValue().increment(velocityKey);
        redisTemplate.expire(velocityKey, 1, TimeUnit.HOURS);
        if (txnCount != null && txnCount > MAX_TRANSACTIONS_PER_HOUR) {
            score += 0.5;
            reasons.add("HIGH_VELOCITY");
        }

        // Rule 3: New IP pattern
        String ipKey = "fraud:ip:" + userId + ":" + ip;
        Boolean isNewIp = redisTemplate.opsForValue().setIfAbsent(ipKey, "1", 30, TimeUnit.DAYS);
        if (Boolean.TRUE.equals(isNewIp)) {
            score += 0.1;
            reasons.add("NEW_IP_ADDRESS");
        }

        FraudScore.FraudRisk risk = score < 0.3 ? FraudScore.FraudRisk.LOW
                : score < 0.5 ? FraudScore.FraudRisk.MEDIUM
                : score < 0.8 ? FraudScore.FraudRisk.HIGH
                : FraudScore.FraudRisk.CRITICAL;

        FraudScore fraudScore = FraudScore.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .score(Math.min(score, 1.0))
                .riskLevel(risk)
                .reasons(reasons.toArray(new String[0]))
                .evaluatedAt(LocalDateTime.now())
                .build();

        if (risk == FraudScore.FraudRisk.HIGH || risk == FraudScore.FraudRisk.CRITICAL) {
            eventProducer.publishEvent(BankingConstants.TOPIC_FRAUD_EVENTS, transactionId,
                    "FRAUD_DETECTED:" + risk.name() + ":" + transactionId);
            log.warn("Fraud detected! Risk: {} Score: {} Transaction: {}", risk, score, transactionId);
        }

        return fraudScore;
    }
}
