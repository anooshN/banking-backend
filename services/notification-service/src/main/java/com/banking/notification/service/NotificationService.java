package com.banking.notification.service;

import com.banking.notification.entity.Notification;
import com.banking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    @KafkaListener(topics = "banking.transaction.events", groupId = "notification-service-group")
    public void handleTransactionEvent(String event, Acknowledgment ack) {
        try {
            log.info("Notification trigger: {}", event);
            // Parse event and create notification
            // In production: parse Avro, extract userId, amount, etc.
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing transaction event for notification", e);
        }
    }

    @KafkaListener(topics = "banking.payment.events", groupId = "notification-service-group")
    public void handlePaymentEvent(String event, Acknowledgment ack) {
        try {
            log.info("Payment notification trigger: {}", event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment event for notification", e);
        }
    }

    public Notification createNotification(String userId, String title, String message,
                                            Notification.NotificationType type, String referenceId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .channel(Notification.NotificationChannel.IN_APP)
                .read(false)
                .referenceId(referenceId)
                .build();
        notification = notificationRepository.save(notification);

        // Push real-time via WebSocket
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", notification);
        return notification;
    }

    public Page<Notification> getUserNotifications(String userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
