package com.banking.payment.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.payment.entity.Payment;
import com.banking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Payment>> initiatePayment(
            @RequestBody Map<String, Object> request) {

        UUID senderAccountId = UUID.fromString((String) request.get("senderAccountId"));
        String receiverAccountNumber = (String) request.get("receiverAccountNumber");
        String receiverBankCode = (String) request.getOrDefault("receiverBankCode", "");
        String receiverName = (String) request.get("receiverName");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String currency = (String) request.getOrDefault("currency", "USD");
        String railStr = (String) request.getOrDefault("paymentRail", "INTERNAL");
        String description = (String) request.getOrDefault("description", "");

        Payment.PaymentRail rail = Payment.PaymentRail.valueOf(railStr);

        Payment payment = paymentService.initiatePayment(
                senderAccountId, receiverAccountNumber, receiverBankCode,
                receiverName, amount, currency, rail, description);

        return ResponseEntity.ok(ApiResponse.success(payment, "Payment initiated successfully"));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Payment>>> getPaymentsByAccount(
            @PathVariable UUID accountId) {
        List<Payment> payments = paymentService.getPaymentsByAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Payment>> getPayment(
            @PathVariable UUID paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }
}
