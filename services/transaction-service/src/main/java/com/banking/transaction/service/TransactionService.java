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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankingEventProducer eventProducer;
    private final AccountServiceClient accountServiceClient;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> transfer(UUID fromAccountId, UUID toAccountId,
                                         UUID userId, BigDecimal amount, String description) {
        // Get balances directly from accounts DB via JDBC
        BigDecimal fromBalance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = ?",
            BigDecimal.class, fromAccountId);

        if (fromBalance == null || fromBalance.compareTo(amount) < 0) {
            throw new BankingException("Insufficient funds", "INSUFFICIENT_FUNDS", HttpStatus.BAD_REQUEST);
        }

        BigDecimal toBalance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = ?",
            BigDecimal.class, toAccountId);

        if (toBalance == null) {
            throw new BankingException("Destination account not found", "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        // Update balances
        jdbcTemplate.update(
            "UPDATE accounts SET balance = balance - ?, available_balance = available_balance - ? WHERE id = ?",
            amount, amount, fromAccountId);
        jdbcTemplate.update(
            "UPDATE accounts SET balance = balance + ?, available_balance = available_balance + ? WHERE id = ?",
            amount, amount, toAccountId);

        String correlationId = UUID.randomUUID().toString();

        // Record debit transaction
        Transaction debitTxn = Transaction.builder()
                .referenceNumber(generateReference())
                .accountId(fromAccountId)
                .userId(userId)
                .transactionType(Transaction.TransactionType.DEBIT)
                .status(Transaction.TransactionStatus.COMPLETED)
                .amount(amount)
                .currencyCode("USD")
                .balanceBefore(fromBalance)
                .balanceAfter(fromBalance.subtract(amount))
                .description(description)
                .correlationId(correlationId)
                .build();
        debitTxn = transactionRepository.save(debitTxn);

        // Record credit transaction
        Transaction creditTxn = Transaction.builder()
                .referenceNumber(generateReference())
                .accountId(toAccountId)
                .userId(userId)
                .transactionType(Transaction.TransactionType.CREDIT)
                .status(Transaction.TransactionStatus.COMPLETED)
                .amount(amount)
                .currencyCode("USD")
                .balanceBefore(toBalance)
                .balanceAfter(toBalance.add(amount))
                .description(description)
                .correlationId(correlationId)
                .build();
        creditTxn = transactionRepository.save(creditTxn);

        log.info("Transfer completed: {} -> {} amount: {}", fromAccountId, toAccountId, amount);

        Map<String, Object> result = new HashMap<>();
        result.put("debitTxn", debitTxn.getReferenceNumber());
        result.put("creditTxn", creditTxn.getReferenceNumber());
        result.put("status", "COMPLETED");
        result.put("amount", amount);
        return result;
    }

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
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var txnPage = transactionRepository.findByAccountId(accountId, pageable);
        return new PageResponse<>(
                txnPage.getContent(),
                txnPage.getNumber(),
                txnPage.getSize(),
                txnPage.getTotalElements(),
                txnPage.getTotalPages(),
                txnPage.isLast(),
                txnPage.isFirst());
    }

    private String generateReference() {
        return "TXN" + System.currentTimeMillis();
    }
}
