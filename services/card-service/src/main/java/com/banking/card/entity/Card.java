package com.banking.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type")
    private CardType cardType;

    @Column(name = "card_number_masked")
    private String cardNumberMasked;  // e.g. **** **** **** 4242

    @Column(name = "card_number_hash")
    private String cardNumberHash;

    @Column(name = "cardholder_name")
    private String cardholderName;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    private CardStatus status;

    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "monthly_limit", precision = 19, scale = 4)
    private BigDecimal monthlyLimit;

    @Column(name = "international_enabled")
    private boolean internationalEnabled;

    @Column(name = "contactless_enabled")
    private boolean contactlessEnabled;

    @Column(name = "online_enabled")
    private boolean onlineEnabled;

    @Column(name = "pin_hash")
    private String pinHash;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CardType { DEBIT, CREDIT, PREPAID, VIRTUAL }
    public enum CardStatus { ACTIVE, BLOCKED, EXPIRED, CANCELLED }
}
