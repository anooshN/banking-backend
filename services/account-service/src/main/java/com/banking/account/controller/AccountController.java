package com.banking.account.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.account.entity.Account;
import com.banking.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<List<Account>>> getUserAccounts(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountsByUserId(userId)));
    }

    @PostMapping("/user/{userId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> createAccount(
            @PathVariable UUID userId,
            @RequestParam Account.AccountType type,
            @RequestParam(defaultValue = "USD") String currency) {
        Account account = accountService.createAccount(userId, type, currency);
        return ResponseEntity.ok(ApiResponse.success(account, "Account created successfully"));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountById(accountId)));
    }

    @GetMapping("/{accountId}/balance")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(@PathVariable UUID accountId) {
        Account account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(ApiResponse.success(account.getBalance()));
    }

    @PatchMapping("/{accountId}/balance")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> updateBalance(
            @PathVariable UUID accountId,
            @RequestParam BigDecimal amount) {
        Account account = accountService.updateBalance(accountId, amount);
        return ResponseEntity.ok(ApiResponse.success(account));
    }
}
