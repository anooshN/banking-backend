package com.banking.account.service;

import com.banking.account.entity.Account;
import com.banking.account.repository.AccountRepository;
import com.banking.audit.annotation.Auditable;
import com.banking.common.constants.BankingConstants;
import com.banking.common.dto.PageResponse;
import com.banking.common.exception.ResourceNotFoundException;
import com.banking.kafka.producer.BankingEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final BankingEventProducer eventProducer;

    @Cacheable(value = BankingConstants.CACHE_ACCOUNT, key = "#accountId")
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
    }

    public List<Account> getAccountsByUserId(UUID userId) {
        return accountRepository.findByUserId(userId);
    }

    @Transactional
    @Auditable(action = "CREATE_ACCOUNT", resource = "Account")
    public Account createAccount(UUID userId, Account.AccountType type, String currency) {
        Account account = Account.builder()
                .userId(userId)
                .accountNumber(generateAccountNumber())
                .accountType(type)
                .status(Account.AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .currencyCode(currency)
                .routingNumber("021000021")
                .overdraftLimit(BigDecimal.ZERO)
                .build();
        account = accountRepository.save(account);
        eventProducer.publishEvent(BankingConstants.TOPIC_ACCOUNT_EVENTS,
                account.getId().toString(), "ACCOUNT_CREATED:" + account.getAccountNumber());
        log.info("Account created: {} for user: {}", account.getAccountNumber(), userId);
        return account;
    }

    @Transactional
    @CacheEvict(value = BankingConstants.CACHE_ACCOUNT, key = "#accountId")
    @Auditable(action = "UPDATE_BALANCE", resource = "Account")
    public Account updateBalance(UUID accountId, BigDecimal amount) {
        Account account = getAccountById(accountId);
        account.setBalance(account.getBalance().add(amount));
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    @CacheEvict(value = BankingConstants.CACHE_ACCOUNT, key = "#accountId")
    @Auditable(action = "FREEZE_ACCOUNT", resource = "Account")
    public Account freezeAccount(UUID accountId) {
        Account account = getAccountById(accountId);
        account.setStatus(Account.AccountStatus.FROZEN);
        return accountRepository.save(account);
    }

    private String generateAccountNumber() {
        return "ACC" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
}
