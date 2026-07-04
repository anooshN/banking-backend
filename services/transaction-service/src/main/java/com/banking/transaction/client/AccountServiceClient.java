package com.banking.transaction.client;

import com.banking.transaction.config.FeignConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "account-service", configuration = FeignConfig.class, fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")
    BigDecimal getBalance(@PathVariable UUID accountId);

    @PatchMapping("/api/v1/accounts/{accountId}/balance")
    @CircuitBreaker(name = "account-service")
    void updateBalance(@PathVariable UUID accountId, @RequestParam BigDecimal amount);
}
