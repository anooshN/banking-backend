package com.banking.notification;

import com.banking.notification.entity.Notification;
import com.banking.notification.repository.NotificationRepository;
import com.banking.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock com.banking.notification.service.EmailService emailService;
    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("Should create in-app notification and push via WebSocket")
    void createNotification_pushesViaWebSocket() {
        Notification saved = Notification.builder()
                .id("notif-123")
                .userId("user-abc")
                .title("Transaction Alert")
                .message("$500 debited")
                .type(Notification.NotificationType.TRANSACTION)
                .read(false)
                .build();

        when(notificationRepository.save(any())).thenReturn(saved);

        Notification result = notificationService.createNotification(
                "user-abc", "Transaction Alert", "$500 debited",
                Notification.NotificationType.TRANSACTION, "txn-999");

        assertThat(result.getUserId()).isEqualTo("user-abc");
        assertThat(result.isRead()).isFalse();
        verify(messagingTemplate).convertAndSendToUser(eq("user-abc"), eq("/queue/notifications"), any());
    }

    @Test
    @DisplayName("Should mark all notifications as read")
    void markAllRead_updatesAll() {
        Notification n1 = Notification.builder().id("1").userId("user-1").read(false).build();
        Notification n2 = Notification.builder().id("2").userId("user-1").read(false).build();

        when(notificationRepository.findByUserIdAndReadFalse("user-1")).thenReturn(List.of(n1, n2));
        when(notificationRepository.saveAll(anyList())).thenReturn(List.of(n1, n2));

        notificationService.markAllRead("user-1");

        verify(notificationRepository).saveAll(argThat(list ->
                ((List<Notification>) list).stream().allMatch(Notification::isRead)));
    }

    @Test
    @DisplayName("Unread count should return correct value")
    void getUnreadCount_returnsCorrect() {
        when(notificationRepository.countByUserIdAndReadFalse("user-1")).thenReturn(7L);
        assertThat(notificationService.getUnreadCount("user-1")).isEqualTo(7L);
    }
}
