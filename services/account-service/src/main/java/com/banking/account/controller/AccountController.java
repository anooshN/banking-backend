package com.banking.account.controller;

import com.banking.account.entity.Account;
import com.banking.account.service.AccountService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account by ID")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountById(accountId)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all accounts for a user")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<List<Account>>> getUserAccounts(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountsByUserId(userId)));
    }

    @PostMapping("/user/{userId}")
    @Operation(summary = "Create a new account")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> createAccount(
            @PathVariable UUID userId,
            @RequestParam Account.AccountType type,
            @RequestParam(defaultValue = "USD") String currency) {
        Account account = accountService.createAccount(userId, type, currency);
        return ResponseEntity.ok(ApiResponse.success(account, "Account created successfully"));
    }

    @PatchMapping("/{accountId}/freeze")
    @Operation(summary = "Freeze an account")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Account>> freezeAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.freezeAccount(accountId)));
    }
}
