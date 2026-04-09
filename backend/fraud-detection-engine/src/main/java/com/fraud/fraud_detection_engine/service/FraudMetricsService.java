package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class FraudMetricsService {

    private final Counter processedCounter;
    private final Counter fraudCounter;
    private final Counter reviewCounter;
    private final Counter allowCounter;
    private final Counter analysisFailureCounter;
    private final Counter alertPublishedCounter;

    public FraudMetricsService(MeterRegistry meterRegistry, TransactionRepository transactionRepository) {
        this.processedCounter = Counter.builder("fraud_transactions_processed_total")
                .description("Total fraud analysis results processed successfully")
                .register(meterRegistry);
        this.fraudCounter = Counter.builder("fraud_transactions_fraud_total")
                .description("Transactions classified as FRAUD")
                .register(meterRegistry);
        this.reviewCounter = Counter.builder("fraud_transactions_review_total")
                .description("Transactions classified as REVIEW")
                .register(meterRegistry);
        this.allowCounter = Counter.builder("fraud_transactions_allow_total")
                .description("Transactions classified as ALLOW")
                .register(meterRegistry);
        this.analysisFailureCounter = Counter.builder("fraud_transactions_failed_total")
                .description("Transactions that failed fraud analysis")
                .register(meterRegistry);
        this.alertPublishedCounter = Counter.builder("fraud_alerts_published_total")
                .description("Fraud alerts published to Kafka")
                .register(meterRegistry);

        Gauge.builder("fraud_pending_transactions", transactionRepository, repo -> repo.countByStatus(Transaction.TransactionStatus.PENDING))
                .description("Transactions waiting for fraud analysis")
                .register(meterRegistry);
    }

    public void recordProcessed(Transaction.FraudVerdict verdict) {
        processedCounter.increment();
        switch (verdict) {
            case FRAUD -> fraudCounter.increment();
            case REVIEW -> reviewCounter.increment();
            default -> allowCounter.increment();
        }
    }

    public void recordAnalysisFailure() {
        analysisFailureCounter.increment();
    }

    public void recordAlertPublished() {
        alertPublishedCounter.increment();
    }
}
