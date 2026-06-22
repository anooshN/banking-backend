package com.banking.notification.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {

    @Id
    private String id;
    private String userId;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationChannel channel;
    private boolean read;
    private String referenceId;
    private String referenceType;

    @CreatedDate
    private LocalDateTime createdAt;

    public enum NotificationType { TRANSACTION, PAYMENT, ACCOUNT, SECURITY, MARKETING, SYSTEM }
    public enum NotificationChannel { EMAIL, SMS, PUSH, IN_APP }
}
