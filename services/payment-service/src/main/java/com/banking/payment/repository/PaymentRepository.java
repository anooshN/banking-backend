package com.banking.payment.repository;

import com.banking.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOutboxProcessedFalse();
    List<Payment> findBySenderAccountId(UUID accountId);
}
