package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudAnalysisResult;
import com.fraud.fraud_detection_engine.dto.FraudStatsResponse;
import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import com.fraud.fraud_detection_engine.dto.TransactionResponse;
import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final KafkaProducerService kafkaProducerService;

    // ??????????????????????????????????????????????????????????????????????????
    //  Phase 1 ? Core transaction processing
    // ??????????????????????????????????????????????????????????????????????????

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Processing transaction [{}] for user [{}]", transactionId, request.getUserId());

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

        // Publish to Kafka for async fraud analysis
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

    // ??????????????????????????????????????????????????????????????????????????
    //  Phase 2 ? Fraud result persistence & new query endpoints
    // ??????????????????????????????????????????????????????????????????????????

    /**
     * Updates an existing transaction record with the results produced by the
     * fraud rule engine. Sets status to COMPLETED (ALLOW/REVIEW) or BLOCKED (FRAUD).
     *
     * @param result the aggregated fraud analysis result
     */
    @Transactional
    public void updateTransactionWithFraudResult(FraudAnalysisResult result) {
        transactionRepository.findByTransactionId(result.getTransactionId())
                .ifPresentOrElse(transaction -> {
                    transaction.setFraudScore(result.getFraudScore());
                    transaction.setFraudVerdict(result.getFraudVerdict());
                    transaction.setTriggeredRules(result.getTriggeredRulesSummary());
                    transaction.setMlScore(result.getMlScore());
                    transaction.setMlModelVersion(result.getMlModelVersion());
                    transaction.setStatus(
                            result.getFraudVerdict() == Transaction.FraudVerdict.FRAUD
                                    ? Transaction.TransactionStatus.BLOCKED
                                    : Transaction.TransactionStatus.COMPLETED
                    );
                    transactionRepository.save(transaction);
                    log.info("Updated transaction [{}] ? verdict={} score={}",
                            result.getTransactionId(), result.getFraudVerdict(),
                            String.format("%.4f", result.getFraudScore()));
                }, () -> log.warn("Transaction [{}] not found in DB ? cannot persist fraud result",
                        result.getTransactionId()));
    }

    /**
     * Fetches a single transaction by its unique transactionId.
     *
     * @param transactionId the UUID string of the transaction
     * @return the transaction response DTO
     * @throws ResponseStatusException 404 if not found
     */
    public TransactionResponse getTransactionById(String transactionId) {
        Transaction tx = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found: " + transactionId));
        return mapToResponse(tx, null);
    }

    /**
     * Returns the transactionId of the most recent PENDING transaction for a user.
     * Used as a best-effort fallback by the Kafka consumer when the message key
     * (transactionId) is not available.
     *
     * @param userId the user to look up
     * @return the transactionId, or null if no pending transaction exists
     */
    public String getLatestPendingTransactionId(String userId) {
        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.PENDING)
                .findFirst()
                .map(Transaction::getTransactionId)
                .orElse(null);
    }

    /**
     * Computes aggregate fraud statistics across all transactions.
     *
     * @return a {@link FraudStatsResponse} with counts and averages
     */
    public FraudStatsResponse getFraudStats() {
        List<Transaction> all = transactionRepository.findAll();

        long total      = all.size();
        long fraud      = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.FRAUD).count();
        long review     = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.REVIEW).count();
        long allow      = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.ALLOW).count();
        long pending    = all.stream().filter(t -> t.getStatus() == Transaction.TransactionStatus.PENDING).count();

        OptionalDouble avgScore = all.stream()
                .filter(t -> t.getFraudScore() != null)
                .mapToDouble(Transaction::getFraudScore)
                .average();

        double fraudRate = total > 0 ? (double) fraud / total * 100.0 : 0.0;

        return FraudStatsResponse.builder()
                .totalTransactions(total)
                .totalFraud(fraud)
                .totalReview(review)
                .totalAllow(allow)
                .totalPending(pending)
                .averageFraudScore(avgScore.orElse(0.0))
                .fraudRate(fraudRate)
                .build();
    }

    // ??????????????????????????????????????????????????????????????????????????
    //  Private helpers
    // ??????????????????????????????????????????????????????????????????????????

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
                .mlScore(tx.getMlScore())
                .mlModelVersion(tx.getMlModelVersion())
                .triggeredRules(tx.getTriggeredRules())
                .message(message)
                .createdAt(tx.getCreatedAt())
                .build();
    }

    /**
     * Returns last N days fraud timeseries (simple in-memory aggregation).
     */
    public List<com.fraud.fraud_detection_engine.dto.FraudTimeseriesPoint> getFraudTimeseries(int days) {
        java.time.LocalDate end = java.time.LocalDate.now();
        java.time.LocalDate start = end.minusDays(days - 1);

        List<Transaction> all = transactionRepository.findAll();

        java.util.Map<java.time.LocalDate, List<Transaction>> grouped =
                all.stream()
                        .filter(t -> t.getCreatedAt() != null)
                        .filter(t -> {
                            java.time.LocalDate d = t.getCreatedAt().toLocalDate();
                            return !d.isBefore(start) && !d.isAfter(end);
                        })
                        .collect(java.util.stream.Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate()));

        List<com.fraud.fraud_detection_engine.dto.FraudTimeseriesPoint> result = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            java.time.LocalDate day = start.plusDays(i);
            List<Transaction> list = grouped.getOrDefault(day, java.util.Collections.emptyList());

            long total = list.size();
            long fraud = list.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.FRAUD).count();
            long review = list.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.REVIEW).count();
            long allow = list.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.ALLOW).count();
            double rate = total == 0 ? 0.0 : (double) fraud / total * 100.0;

            result.add(com.fraud.fraud_detection_engine.dto.FraudTimeseriesPoint.builder()
                    .date(day.toString())
                    .total(total)
                    .fraud(fraud)
                    .review(review)
                    .allow(allow)
                    .fraudRate(rate)
                    .build());
        }

        return result;
    }
}
