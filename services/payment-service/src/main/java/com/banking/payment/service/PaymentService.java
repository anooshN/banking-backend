package com.banking.payment.service;

import com.banking.audit.annotation.Auditable;
import com.banking.common.constants.BankingConstants;
import com.banking.common.exception.BankingException;
import com.banking.kafka.producer.BankingEventProducer;
import com.banking.payment.entity.Payment;
import com.banking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BankingEventProducer eventProducer;

    @Transactional
    @Auditable(action = "INITIATE_PAYMENT", resource = "Payment")
    public Payment initiatePayment(UUID senderAccountId, String receiverAccount,
                                   String receiverBankCode, String receiverName,
                                   BigDecimal amount, String currency,
                                   Payment.PaymentRail rail, String description) {
        Payment payment = Payment.builder()
                .paymentReference(generatePaymentRef(rail))
                .senderAccountId(senderAccountId)
                .receiverAccountNumber(receiverAccount)
                .receiverBankCode(receiverBankCode)
                .receiverName(receiverName)
                .amount(amount)
                .currencyCode(currency)
                .paymentRail(rail)
                .status(Payment.PaymentStatus.INITIATED)
                .description(description)
                .outboxProcessed(false)
                .build();

        if (rail == Payment.PaymentRail.SWIFT) {
            payment.setSwiftMessage(buildSwiftMT103(payment));
        }

        payment = paymentRepository.save(payment);
        log.info("Payment initiated: {} via {}", payment.getPaymentReference(), rail);
        return payment;
    }

    public List<Payment> getPaymentsByAccount(UUID accountId) {
        return paymentRepository.findBySenderAccountId(accountId);
    }

    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BankingException("Payment not found", "PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<Payment> unprocessed = paymentRepository.findByOutboxProcessedFalse();
        for (Payment payment : unprocessed) {
            try {
                eventProducer.publishEvent(BankingConstants.TOPIC_PAYMENT_EVENTS,
                        payment.getId().toString(),
                        "PAYMENT_INITIATED:" + payment.getPaymentReference() + ":" + payment.getPaymentRail());
                payment.setOutboxProcessed(true);
                paymentRepository.save(payment);
            } catch (Exception e) {
                log.error("Failed to process outbox for payment: {}", payment.getId(), e);
            }
        }
    }

    private String generatePaymentRef(Payment.PaymentRail rail) {
        return rail.name().substring(0, 3) + System.currentTimeMillis();
    }

    private String buildSwiftMT103(Payment payment) {
        return String.format(":20:%s:32A:%s%s%.2f:59:%s",
                payment.getPaymentReference(),
                java.time.LocalDate.now(),
                payment.getCurrencyCode(),
                payment.getAmount(),
                payment.getReceiverName());
    }
}
