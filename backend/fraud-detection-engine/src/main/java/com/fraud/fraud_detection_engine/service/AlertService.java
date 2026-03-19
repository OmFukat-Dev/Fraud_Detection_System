package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudAnalysisResult;
import com.fraud.fraud_detection_engine.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Handles fraud alert notifications.
 *
 * <p>For FRAUD and REVIEW verdicts, this service:
 * <ol>
 *   <li>Emits a structured log entry at WARN or ERROR level.</li>
 *   <li>Publishes the {@link FraudAnalysisResult} to the {@code fraud.alerts} Kafka topic
 *       for downstream notification services.</li>
 * </ol>
 *
 * <p>ALLOW verdicts are silently ignored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.alerts}")
    private String alertsTopic;

    /**
     * Processes a completed fraud analysis result and fires an alert if warranted.
     *
     * @param result the fully evaluated analysis result
     */
    public void handleResult(FraudAnalysisResult result) {
        switch (result.getFraudVerdict()) {
            case FRAUD -> {
                log.error("[FRAUD ALERT] Transaction [{}] classified as FRAUD — score={} rules=[{}]",
                        result.getTransactionId(),
                        String.format("%.4f", result.getFraudScore()),
                        result.getTriggeredRulesSummary());
                publishAlert(result);
            }
            case REVIEW -> {
                log.warn("[REVIEW ALERT] Transaction [{}] flagged for REVIEW — score={} rules=[{}]",
                        result.getTransactionId(),
                        String.format("%.4f", result.getFraudScore()),
                        result.getTriggeredRulesSummary());
                publishAlert(result);
            }
            default -> log.debug("Transaction [{}] cleared — score={} verdict=ALLOW",
                    result.getTransactionId(),
                    String.format("%.4f", result.getFraudScore()));
        }
    }

    /**
     * Publishes the fraud analysis result to the {@code fraud.alerts} Kafka topic.
     * Failures are logged but do not propagate to the caller (best-effort delivery).
     *
     * @param result the analysis result to publish
     */
    private void publishAlert(FraudAnalysisResult result) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(alertsTopic, result.getTransactionId(), result);

        future.whenComplete((sendResult, ex) -> {
            if (ex == null) {
                log.info("Alert published for transaction [{}] to topic [{}]",
                        result.getTransactionId(), alertsTopic);
            } else {
                log.error("Failed to publish alert for transaction [{}]: {}",
                        result.getTransactionId(), ex.getMessage());
            }
        });
    }
}
