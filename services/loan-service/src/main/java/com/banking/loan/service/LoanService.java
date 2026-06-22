package com.banking.loan.service;

import com.banking.common.exception.ResourceNotFoundException;
import com.banking.loan.entity.Loan;
import com.banking.loan.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;

    @Transactional
    public Loan applyForLoan(UUID userId, UUID accountId, Loan.LoanType type,
                              BigDecimal amount, int tenureMonths, BigDecimal interestRate, String purpose) {
        BigDecimal emi = calculateEmi(amount, interestRate, tenureMonths);
        Loan loan = Loan.builder()
                .loanNumber("LOAN" + System.currentTimeMillis())
                .userId(userId)
                .accountId(accountId)
                .loanType(type)
                .status(Loan.LoanStatus.APPLIED)
                .principalAmount(amount)
                .outstandingBalance(amount)
                .interestRate(interestRate)
                .tenureMonths(tenureMonths)
                .emiAmount(emi)
                .purpose(purpose)
                .build();
        log.info("Loan application: {} for user: {} amount: {}", type, userId, amount);
        return loanRepository.save(loan);
    }

    public List<Loan> getUserLoans(UUID userId) {
        return loanRepository.findByUserId(userId);
    }

    public Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId.toString()));
    }

    // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int months) {
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal power = onePlusR.pow(months, new MathContext(10));
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(power);
        BigDecimal denominator = power.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
