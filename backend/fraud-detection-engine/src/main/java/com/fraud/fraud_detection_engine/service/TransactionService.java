package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import com.fraud.fraud_detection_engine.dto.TransactionResponse;
import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Processing transaction [{}] for user [{}]", transactionId, request.getUserId());

        // Build and save transaction entity
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .deviceId(request.getDeviceId())
                .ipAddress(request.getIpAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(Transaction.TransactionStatus.PENDING)
                .fraudVerdict(Transaction.FraudVerdict.ALLOW)
                .fraudScore(0.0)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Publish to Kafka for async fraud analysis (Phase 2+)
        kafkaProducerService.publishTransaction(transactionId, request);

        log.info("Transaction [{}] saved and published to Kafka", transactionId);

        return mapToResponse(saved, "Transaction received and queued for fraud analysis");
    }

    public List<TransactionResponse> getTransactionsByUser(String userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(t -> mapToResponse(t, null))
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getFlaggedTransactions() {
        return transactionRepository.findByFraudVerdictOrderByCreatedAtDesc(Transaction.FraudVerdict.FRAUD)
                .stream()
                .map(t -> mapToResponse(t, null))
                .collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction tx, String message) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionId(tx.getTransactionId())
                .userId(tx.getUserId())
                .merchantId(tx.getMerchantId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .fraudVerdict(tx.getFraudVerdict())
                .fraudScore(tx.getFraudScore())
                .triggeredRules(tx.getTriggeredRules())
                .message(message)
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
