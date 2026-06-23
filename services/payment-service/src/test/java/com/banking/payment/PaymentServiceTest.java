package com.banking.payment;

import com.banking.kafka.producer.BankingEventProducer;
import com.banking.payment.entity.Payment;
import com.banking.payment.repository.PaymentRepository;
import com.banking.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock BankingEventProducer eventProducer;
    @InjectMocks PaymentService paymentService;

    private UUID senderAccountId;

    @BeforeEach
    void setUp() {
        senderAccountId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should initiate SWIFT payment and store outbox=false")
    void initiatePayment_swift_storesUnprocessed() {
        Payment saved = Payment.builder()
                .id(UUID.randomUUID())
                .senderAccountId(senderAccountId)
                .receiverAccountNumber("GB29NWBK60161331926819")
                .receiverBankCode("NWBKGB2L")
                .receiverName("Jane Smith")
                .amount(new BigDecimal("5000.00"))
                .currencyCode("USD")
                .paymentRail(Payment.PaymentRail.SWIFT)
                .status(Payment.PaymentStatus.INITIATED)
                .outboxProcessed(false)
                .paymentReference("SWI" + System.currentTimeMillis())
                .build();

        when(paymentRepository.save(any())).thenReturn(saved);

        Payment result = paymentService.initiatePayment(
                senderAccountId, "GB29NWBK60161331926819", "NWBKGB2L",
                "Jane Smith", new BigDecimal("5000.00"), "USD",
                Payment.PaymentRail.SWIFT, "Invoice #456");

        assertThat(result).isNotNull();
        assertThat(result.getPaymentRail()).isEqualTo(Payment.PaymentRail.SWIFT);
        assertThat(result.isOutboxProcessed()).isFalse();
        assertThat(result.getSwiftMessage()).isNotNull();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should process outbox and publish Kafka events")
    void processOutbox_publishesEvents() {
        Payment unprocessed = Payment.builder()
                .id(UUID.randomUUID())
                .paymentReference("ACH123")
                .paymentRail(Payment.PaymentRail.ACH)
                .outboxProcessed(false)
                .build();

        when(paymentRepository.findByOutboxProcessedFalse()).thenReturn(List.of(unprocessed));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).publishEvent(any(), any(), any());

        paymentService.processOutbox();

        verify(eventProducer).publishEvent(any(), any(), any());
        verify(paymentRepository).save(argThat(p -> ((Payment) p).isOutboxProcessed()));
    }

    @Test
    @DisplayName("INTERNAL payment should not have SWIFT message")
    void initiatePayment_internal_noSwiftMessage() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.initiatePayment(
                senderAccountId, "ACC987654", null,
                "Internal Transfer", new BigDecimal("100.00"), "USD",
                Payment.PaymentRail.INTERNAL, "Rent");

        assertThat(result.getSwiftMessage()).isNull();
    }
}
