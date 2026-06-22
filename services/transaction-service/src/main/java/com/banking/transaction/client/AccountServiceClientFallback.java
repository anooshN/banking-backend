package com.banking.transaction.client;

import com.banking.common.exception.BankingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public BigDecimal getBalance(UUID accountId) {
        throw new BankingException("Account service unavailable", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public void updateBalance(UUID accountId, BigDecimal amount) {
        throw new BankingException("Account service unavailable", "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
