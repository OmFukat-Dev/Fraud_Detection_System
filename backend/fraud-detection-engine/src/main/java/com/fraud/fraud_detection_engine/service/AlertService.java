package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FraudAlertBroadcaster broadcaster;
    private final FraudMetricsService fraudMetricsService;

    @Value("${app.kafka.topic.alerts}")
    private String alertsTopic;

    public void handleResult(FraudAnalysisResult result) {
        switch (result.getFraudVerdict()) {
            case FRAUD -> {
                log.error("[FRAUD ALERT] Transaction [{}] classified as FRAUD — score={} rules=[{}]",
                        result.getTransactionId(),
                        String.format("%.4f", result.getFraudScore()),
                        result.getTriggeredRulesSummary());
                broadcaster.broadcast(result);
publishAlert(result);
            }
            case REVIEW -> {
                log.warn("[REVIEW ALERT] Transaction [{}] flagged for REVIEW — score={} rules=[{}]",
                        result.getTransactionId(),
                        String.format("%.4f", result.getFraudScore()),
                        result.getTriggeredRulesSummary());
                broadcaster.broadcast(result);
            }
            default -> log.debug("Transaction [{}] cleared — score={} verdict=ALLOW",
                    result.getTransactionId(),
                    String.format("%.4f", result.getFraudScore()));
        }
    }

    private void publishAlert(FraudAnalysisResult result) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(alertsTopic, result.getTransactionId(), result);

        future.whenComplete((sendResult, ex) -> {
            if (ex == null) {
                log.info("Alert published for transaction [{}] to topic [{}]",
                        result.getTransactionId(), alertsTopic);
                fraudMetricsService.recordAlertPublished();
            } else {
                log.error("Failed to publish alert for transaction [{}]: {}",
                        result.getTransactionId(), ex.getMessage());
            }
        });
    }
}
