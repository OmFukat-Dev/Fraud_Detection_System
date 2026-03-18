package com.fraud.fraud_detection_engine.service;

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
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.transactions}")
    private String transactionsTopic;

    public void publishTransaction(String transactionId, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(transactionsTopic, transactionId, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Transaction [{}] published to topic [{}] partition [{}] offset [{}]",
                        transactionId,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish transaction [{}]: {}", transactionId, ex.getMessage());
            }
        });
    }
}
