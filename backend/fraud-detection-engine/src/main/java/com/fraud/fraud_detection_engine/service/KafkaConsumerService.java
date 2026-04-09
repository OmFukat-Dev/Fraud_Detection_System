package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudAnalysisResult;
import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer that listens on the {@code fraud.transactions.raw} topic.
 *
 * <p>For each incoming transaction message, this service orchestrates the
 * full fraud analysis pipeline:
 * <ol>
 *   <li>Deserialise the {@link TransactionRequest} payload.</li>
 *   <li>Pass it through the {@link FraudRuleEngine}.</li>
 *   <li>Persist the result back to MySQL via {@link TransactionService}.</li>
 *   <li>Publish an alert via {@link AlertService} if warranted.</li>
 * </ol>
 *
 * <p>Errors are caught and logged; a failed message is <em>not</em> re-queued
 * here (dead-letter queue configuration can be added in a later phase).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final FraudRuleEngine fraudRuleEngine;
    private final TransactionService transactionService;
    private final AlertService alertService;
    private final FraudMetricsService fraudMetricsService;


    /**
     * Overloaded listener that also receives the message key (= transactionId)
     * set by {@link KafkaProducerService}.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.transactions}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionWithKey(
            @Payload TransactionRequest request,
            @Header(KafkaHeaders.RECEIVED_KEY) String transactionId,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed transaction [{}] from topic [{}] partition [{}] offset [{}]",
                transactionId, topic, partition, offset);

        try {
            // 1. Run fraud analysis through the rule engine
            FraudAnalysisResult result = fraudRuleEngine.analyze(transactionId, request);

            // 2. Persist the fraud result back to MySQL
            transactionService.updateTransactionWithFraudResult(result);

            // 3. Fire alert if FRAUD or REVIEW
            alertService.handleResult(result);
            fraudMetricsService.recordProcessed(result.getFraudVerdict());

        } catch (Exception e) {
            log.error("Error processing fraud analysis for transaction [{}]: {}",
                    transactionId, e.getMessage(), e);
            fraudMetricsService.recordAnalysisFailure();
            // In production: route to a dead-letter topic instead of silently dropping
        }
    }

}
