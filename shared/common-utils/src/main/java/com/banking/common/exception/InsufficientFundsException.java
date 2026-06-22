package com.banking.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(String accountId) {
        super(
            String.format("Insufficient funds in account: %s", accountId),
            "INSUFFICIENT_FUNDS",
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
