package com.banking.loan.repository;

import com.banking.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByUserId(UUID userId);
    List<Loan> findByNextEmiDateLessThanEqualAndStatus(LocalDate date, Loan.LoanStatus status);
}
