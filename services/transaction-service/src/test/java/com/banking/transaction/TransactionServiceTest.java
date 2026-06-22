package com.banking.transaction;

import com.banking.common.exception.InsufficientFundsException;
import com.banking.kafka.producer.BankingEventProducer;
import com.banking.transaction.client.AccountServiceClient;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private BankingEventProducer eventProducer;
    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private TransactionService transactionService;

    private UUID accountId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should debit successfully when balance is sufficient")
    void debit_sufficientBalance_success() {
        when(accountServiceClient.getBalance(accountId)).thenReturn(new BigDecimal("5000.00"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).publishEvent(any(), any(), any());

        Transaction txn = transactionService.debit(accountId, userId,
                new BigDecimal("100.00"), "Test debit", "corr-123");

        assertThat(txn).isNotNull();
        assertThat(txn.getTransactionType()).isEqualTo(Transaction.TransactionType.DEBIT);
        assertThat(txn.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(eventProducer).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when balance too low")
    void debit_insufficientBalance_throws() {
        when(accountServiceClient.getBalance(accountId)).thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> transactionService.debit(accountId, userId,
                new BigDecimal("100.00"), "Test debit", "corr-123"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Should credit account successfully")
    void credit_success() {
        when(accountServiceClient.getBalance(accountId)).thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).publishEvent(any(), any(), any());

        Transaction txn = transactionService.credit(accountId, userId,
                new BigDecimal("500.00"), "Test credit", "corr-456");

        assertThat(txn.getTransactionType()).isEqualTo(Transaction.TransactionType.CREDIT);
        assertThat(txn.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }
}
