package com.banking.account;

import com.banking.account.entity.Account;
import com.banking.account.repository.AccountRepository;
import com.banking.account.service.AccountService;
import com.banking.common.exception.InsufficientFundsException;
import com.banking.common.exception.ResourceNotFoundException;
import com.banking.kafka.producer.BankingEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BankingEventProducer eventProducer;

    @InjectMocks
    private AccountService accountService;

    private UUID userId;
    private UUID accountId;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        testAccount = Account.builder()
                .id(accountId)
                .userId(userId)
                .accountNumber("ACC123456")
                .accountType(Account.AccountType.CHECKING)
                .status(Account.AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .currencyCode("USD")
                .build();
    }

    @Test
    @DisplayName("Should create account successfully")
    void createAccount_success() {
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        doNothing().when(eventProducer).publishEvent(any(), any(), any());

        Account result = accountService.createAccount(userId, Account.AccountType.CHECKING, "USD");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        verify(accountRepository).save(any(Account.class));
        verify(eventProducer).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("Should return account by ID")
    void getAccountById_found() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        Account result = accountService.getAccountById(accountId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account not found")
    void getAccountById_notFound_throws() {
        when(accountRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should update balance correctly")
    void updateBalance_credit() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal creditAmount = new BigDecimal("500.00");
        Account updated = accountService.updateBalance(accountId, creditAmount);

        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("10500.00"));
    }

    @Test
    @DisplayName("Should get all accounts for user")
    void getAccountsByUserId_returnsAll() {
        when(accountRepository.findByUserId(userId)).thenReturn(List.of(testAccount));

        List<Account> results = accountService.getAccountsByUserId(userId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should freeze account")
    void freezeAccount_success() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account frozen = accountService.freezeAccount(accountId);

        assertThat(frozen.getStatus()).isEqualTo(Account.AccountStatus.FROZEN);
    }
}
