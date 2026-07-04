package com.banking.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_reference", unique = true, nullable = false)
    private String paymentReference;

    @Column(name = "sender_account_id", nullable = false)
    private UUID senderAccountId;

    @Column(name = "receiver_account_number", nullable = false)
    private String receiverAccountNumber;

    @Column(name = "receiver_bank_code")
    private String receiverBankCode;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_rail")
    private PaymentRail paymentRail;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String description;

    @Column(name = "swift_message")
    private String swiftMessage;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "outbox_processed")
    private boolean outboxProcessed;

    @Builder.Default
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PaymentRail { SWIFT, FEDWIRE, CHIPS, INTERNAL, ACH }
    public enum PaymentStatus { INITIATED, PROCESSING, COMPLETED, FAILED, REVERSED }
}
