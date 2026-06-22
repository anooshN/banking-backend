package com.banking.transaction.service;

import com.banking.audit.annotation.Auditable;
import com.banking.common.constants.BankingConstants;
import com.banking.common.dto.PageResponse;
import com.banking.common.exception.BankingException;
import com.banking.common.exception.InsufficientFundsException;
import com.banking.kafka.producer.BankingEventProducer;
import com.banking.transaction.client.AccountServiceClient;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankingEventProducer eventProducer;
    private final AccountServiceClient accountServiceClient;

    @Transactional
    @Auditable(action = "DEBIT", resource = "Transaction")
    public Transaction debit(UUID accountId, UUID userId, BigDecimal amount,
                              String description, String correlationId) {
        BigDecimal currentBalance = accountServiceClient.getBalance(accountId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId.toString());
        }
        Transaction txn = Transaction.builder()
                .referenceNumber(generateReference())
                .accountId(accountId)
                .userId(userId)
                .transactionType(Transaction.TransactionType.DEBIT)
                .status(Transaction.TransactionStatus.PENDING)
                .amount(amount)
                .currencyCode("USD")
                .balanceBefore(currentBalance)
                .balanceAfter(currentBalance.subtract(amount))
                .description(description)
                .correlationId(correlationId)
                .build();
        txn = transactionRepository.save(txn);

        // Publish Kafka event (Saga step)
        eventProducer.publishEvent(BankingConstants.TOPIC_TRANSACTION_EVENTS,
                txn.getId().toString(), "DEBIT_INITIATED:" + txn.getReferenceNumber());
        return txn;
    }

    @Transactional
    @Auditable(action = "CREDIT", resource = "Transaction")
    public Transaction credit(UUID accountId, UUID userId, BigDecimal amount,
                               String description, String correlationId) {
        BigDecimal currentBalance = accountServiceClient.getBalance(accountId);
        Transaction txn = Transaction.builder()
                .referenceNumber(generateReference())
                .accountId(accountId)
                .userId(userId)
                .transactionType(Transaction.TransactionType.CREDIT)
                .status(Transaction.TransactionStatus.PENDING)
                .amount(amount)
                .currencyCode("USD")
                .balanceBefore(currentBalance)
                .balanceAfter(currentBalance.add(amount))
                .description(description)
                .correlationId(correlationId)
                .build();
        txn = transactionRepository.save(txn);
        eventProducer.publishEvent(BankingConstants.TOPIC_TRANSACTION_EVENTS,
                txn.getId().toString(), "CREDIT_INITIATED:" + txn.getReferenceNumber());
        return txn;
    }

    public PageResponse<Transaction> getTransactionHistory(UUID accountId, int page, int size) {
        return PageResponse.of(
            transactionRepository.findByAccountId(accountId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
        );
    }

    private String generateReference() {
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
