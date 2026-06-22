package com.banking.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BankingException {

    public ResourceNotFoundException(String resource, String id) {
        super(
            String.format("%s not found with id: %s", resource, id),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND
        );
    }
}
