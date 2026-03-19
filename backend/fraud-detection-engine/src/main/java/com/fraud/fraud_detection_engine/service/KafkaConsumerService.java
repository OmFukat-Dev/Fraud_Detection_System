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

    /**
     * Consumes a transaction event from {@code fraud.transactions.raw} and
     * runs the complete fraud analysis pipeline.
     *
     * @param request   the deserialized transaction payload
     * @param topic     the Kafka topic name (injected from message headers)
     * @param partition the partition this message was read from
     * @param offset    the message offset within the partition
     */
    @KafkaListener(
            topics = "${app.kafka.topic.transactions}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransaction(
            @Payload TransactionRequest request,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed transaction from topic [{}] partition [{}] offset [{}] — userId={}",
                topic, partition, offset, request.getUserId());

        // We need the transactionId, but TransactionRequest does not carry it
        // because the ID was generated during save. We retrieve it from the DB
        // via a dedicated lookup after the initial save.
        // For the consumer, we rely on the Kafka message key (= transactionId)
        // which was set by KafkaProducerService.publishTransaction(transactionId, request).
        // However @Header(KafkaHeaders.RECEIVED_KEY) needs to be added for that.
        // As a robust fallback we pass the payload directly and let the rule engine
        // use a generated placeholder that gets reconciled during updateTransactionWithFraudResult.
        processTransaction(request);
    }

    /**
     * Overloaded listener that also receives the message key (= transactionId)
     * set by {@link KafkaProducerService}.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.transactions}",
            groupId = "${spring.kafka.consumer.group-id}-keyed",
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

        } catch (Exception e) {
            log.error("Error processing fraud analysis for transaction [{}]: {}",
                    transactionId, e.getMessage(), e);
            // In production: route to a dead-letter topic instead of silently dropping
        }
    }

    /**
     * Fallback: processes transactions where the key could not be extracted.
     * Looks up the most recent PENDING transaction for this user as a best-effort match.
     */
    private void processTransaction(TransactionRequest request) {
        log.warn("Processing transaction without key for user [{}] — attempting best-effort lookup",
                request.getUserId());
        try {
            // Try to find the most recent pending transaction for this user
            String transactionId = transactionService.getLatestPendingTransactionId(request.getUserId());
            if (transactionId != null) {
                FraudAnalysisResult result = fraudRuleEngine.analyze(transactionId, request);
                transactionService.updateTransactionWithFraudResult(result);
                alertService.handleResult(result);
            } else {
                log.warn("No pending transaction found for user [{}] — skipping fraud analysis",
                        request.getUserId());
            }
        } catch (Exception e) {
            log.error("Fallback processing failed for user [{}]: {}", request.getUserId(), e.getMessage(), e);
        }
    }
}
