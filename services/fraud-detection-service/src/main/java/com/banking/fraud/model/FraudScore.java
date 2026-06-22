package com.banking.fraud.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FraudScore {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private double score;          // 0.0 - 1.0
    private FraudRisk riskLevel;
    private String[] reasons;
    private LocalDateTime evaluatedAt;

    public enum FraudRisk { LOW, MEDIUM, HIGH, CRITICAL }
}
