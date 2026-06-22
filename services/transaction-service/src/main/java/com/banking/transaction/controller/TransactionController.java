package com.banking.transaction.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.common.dto.PageResponse;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction processing and history")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get transaction history for an account")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<PageResponse<Transaction>>> getHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                transactionService.getTransactionHistory(accountId, page, size)));
    }
}
