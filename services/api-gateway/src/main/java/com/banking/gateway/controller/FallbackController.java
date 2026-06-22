package com.banking.gateway.controller;

import com.banking.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<Void>> userFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("User service is currently unavailable", "SERVICE_UNAVAILABLE"));
    }

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<Void>> accountFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Account service is currently unavailable", "SERVICE_UNAVAILABLE"));
    }

    @GetMapping("/transaction")
    public ResponseEntity<ApiResponse<Void>> transactionFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Transaction service is currently unavailable", "SERVICE_UNAVAILABLE"));
    }
}
