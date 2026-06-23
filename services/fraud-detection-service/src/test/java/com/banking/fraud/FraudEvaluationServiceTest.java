package com.banking.fraud;

import com.banking.fraud.model.FraudScore;
import com.banking.fraud.service.FraudEvaluationService;
import com.banking.kafka.producer.BankingEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudEvaluationServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock BankingEventProducer eventProducer;
    @InjectMocks FraudEvaluationService fraudService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("Low amount should score LOW risk")
    void evaluate_smallAmount_lowRisk() {
        FraudScore score = fraudService.evaluate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new BigDecimal("50.00"),
                "192.168.1.1");

        assertThat(score.getRiskLevel()).isEqualTo(FraudScore.FraudRisk.LOW);
        assertThat(score.getScore()).isLessThan(0.3);
    }

    @Test
    @DisplayName("High value transaction should increase score")
    void evaluate_highValue_raisesScore() {
        FraudScore score = fraudService.evaluate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new BigDecimal("50000.00"),
                "192.168.1.1");

        assertThat(score.getScore()).isGreaterThanOrEqualTo(0.3);
        assertThat(score.getReasons()).contains("HIGH_VALUE_TRANSACTION");
    }

    @Test
    @DisplayName("High velocity should trigger HIGH risk and Kafka event")
    void evaluate_highVelocity_triggersAlert() {
        when(valueOperations.increment(anyString())).thenReturn(25L); // > 20 threshold

        FraudScore score = fraudService.evaluate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new BigDecimal("100.00"),
                "10.0.0.1");

        assertThat(score.getRiskLevel()).isIn(
                FraudScore.FraudRisk.HIGH, FraudScore.FraudRisk.CRITICAL);
        assertThat(score.getReasons()).contains("HIGH_VELOCITY");
        verify(eventProducer).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("New IP address should add to score")
    void evaluate_newIp_addsScore() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        FraudScore score = fraudService.evaluate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new BigDecimal("200.00"),
                "203.0.113.50");

        assertThat(score.getReasons()).contains("NEW_IP_ADDRESS");
    }
}
