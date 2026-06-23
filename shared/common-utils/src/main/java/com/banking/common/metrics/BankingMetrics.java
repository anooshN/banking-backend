package com.banking.common.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class BankingMetrics {

    private final Counter transactionCounter;
    private final Counter transactionFailedCounter;
    private final Counter insufficientFundsCounter;
    private final Counter fraudDetectedCounter;
    private final DistributionSummary transactionAmountSummary;
    private final DistributionSummary fraudScoreSummary;
    private final AtomicLong activeSessionsGauge;

    public BankingMetrics(MeterRegistry registry) {
        this.transactionCounter = Counter.builder("banking.transaction.total")
                .description("Total number of transactions processed")
                .tag("service", "transaction-service")
                .register(registry);

        this.transactionFailedCounter = Counter.builder("banking.transaction.failed")
                .description("Total number of failed transactions")
                .register(registry);

        this.insufficientFundsCounter = Counter.builder("banking.insufficient.funds")
                .description("Number of insufficient funds errors")
                .register(registry);

        this.fraudDetectedCounter = Counter.builder("banking.fraud.detected")
                .description("Number of fraud detections")
                .register(registry);

        this.transactionAmountSummary = DistributionSummary.builder("banking.transaction.amount")
                .description("Transaction amount distribution in USD")
                .baseUnit("USD")
                .scale(0.01)
                .register(registry);

        this.fraudScoreSummary = DistributionSummary.builder("banking.fraud.score")
                .description("Fraud score distribution")
                .register(registry);

        this.activeSessionsGauge = registry.gauge(
                "banking.active.sessions",
                new AtomicLong(0));
    }

    public void recordTransaction(BigDecimal amount) {
        transactionCounter.increment();
        transactionAmountSummary.record(amount.doubleValue());
    }

    public void recordFailedTransaction() {
        transactionFailedCounter.increment();
    }

    public void recordInsufficientFunds() {
        insufficientFundsCounter.increment();
    }

    public void recordFraudDetected(double score, String riskLevel) {
        fraudDetectedCounter.increment();
        fraudScoreSummary.record(score);
    }

    public void setActiveSessions(long count) {
        if (activeSessionsGauge != null) {
            activeSessionsGauge.set(count);
        }
    }
}
