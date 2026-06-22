package com.banking.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published event to topic: {} partition: {} offset: {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event to topic: {} key: {} error: {}",
                        topic, key, ex.getMessage());
            }
        });
    }

    public CompletableFuture<SendResult<String, Object>> publishEventAsync(String topic, String key, Object event) {
        return kafkaTemplate.send(topic, key, event);
    }
}
