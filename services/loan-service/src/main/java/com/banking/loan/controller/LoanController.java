package com.banking.loan.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.loan.entity.Loan;
import com.banking.loan.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;

    @GetMapping
    @Operation(summary = "Get all loans for current user")
    public ResponseEntity<ApiResponse<List<Loan>>> getMyLoans(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(loanService.getUserLoans(UUID.fromString(userId))));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan details")
    public ResponseEntity<ApiResponse<Loan>> getLoan(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.success(loanService.getLoan(loanId)));
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply for a loan")
    public ResponseEntity<ApiResponse<Loan>> applyForLoan(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam UUID accountId,
            @RequestParam Loan.LoanType loanType,
            @RequestParam BigDecimal amount,
            @RequestParam int tenureMonths,
            @RequestParam BigDecimal interestRate,
            @RequestParam String purpose) {
        Loan loan = loanService.applyForLoan(UUID.fromString(userId), accountId,
                loanType, amount, tenureMonths, interestRate, purpose);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(loan, "Loan application submitted successfully"));
    }
}
