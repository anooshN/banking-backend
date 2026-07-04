package com.banking.transaction.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.common.dto.PageResponse;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<PageResponse<Transaction>>> getHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                transactionService.getTransactionHistory(accountId, page, size)));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transfer(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        UUID fromAccountId = UUID.fromString((String) request.get("fromAccountId"));
        UUID toAccountId = UUID.fromString((String) request.get("toAccountId"));
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String description = (String) request.getOrDefault("description", "Transfer");
        UUID userId = UUID.fromString(authentication.getName());
        String correlationId = UUID.randomUUID().toString();

        Transaction debit = transactionService.debit(fromAccountId, userId, amount, description, correlationId);
        Transaction credit = transactionService.credit(toAccountId, userId, amount, description, correlationId);

        return ResponseEntity.ok(ApiResponse.success(
            Map.of("debitTxn", debit.getReferenceNumber(),
                   "creditTxn", credit.getReferenceNumber(),
                   "status", "COMPLETED"),
            "Transfer completed successfully"));
    }
}
