package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.FraudGraphResponse;
import com.fraud.fraud_detection_engine.dto.FraudMerchantCountResponse;
import com.fraud.fraud_detection_engine.dto.FraudScoreDistributionBucket;
import com.fraud.fraud_detection_engine.dto.FraudStatsResponse;
import com.fraud.fraud_detection_engine.dto.FraudTimeseriesPoint;
import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.repository.TransactionRepository;
import com.fraud.fraud_detection_engine.service.FraudGraphService;
import com.fraud.fraud_detection_engine.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudStatsController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final FraudGraphService fraudGraphService;

    @GetMapping("/stats")
    public ResponseEntity<FraudStatsResponse> getFraudStats() {
        log.info("Fetching fraud statistics");
        return ResponseEntity.ok(buildFraudStats());
    }

    @GetMapping("/timeseries")
    public ResponseEntity<List<FraudTimeseriesPoint>> getTimeseries(@RequestParam(defaultValue = "6") int days) {
        return ResponseEntity.ok(transactionService.getFraudTimeseries(days));
    }

    @GetMapping("/analytics/top-merchants")
    public ResponseEntity<List<FraudMerchantCountResponse>> getTopMerchants(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(buildTopMerchants(limit));
    }

    @GetMapping("/analytics/score-distribution")
    public ResponseEntity<List<FraudScoreDistributionBucket>> getScoreDistribution() {
        return ResponseEntity.ok(buildScoreDistribution());
    }

    @GetMapping("/analytics/graph")
    public ResponseEntity<FraudGraphResponse> getGraphAnalysis(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(fraudGraphService.buildGraphAnalysis(limit));
    }

    private FraudStatsResponse buildFraudStats() {
        List<Transaction> all = transactionRepository.findAll();
        LocalDate today = LocalDate.now();
        long total = all.size();
        long fraud = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.FRAUD).count();
        long review = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.REVIEW).count();
        long allow = all.stream().filter(t -> t.getFraudVerdict() == Transaction.FraudVerdict.ALLOW).count();
        long pending = all.stream().filter(t -> t.getStatus() == Transaction.TransactionStatus.PENDING).count();
        long flaggedToday = all.stream()
                .filter(this::isFlagged)
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().equals(today))
                .count();
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
                .flaggedToday(flaggedToday)
                .averageFraudScore(avgScore.orElse(0.0))
                .fraudRate(fraudRate)
                .build();
    }

    private List<FraudMerchantCountResponse> buildTopMerchants(int limit) {
        int safeLimit = Math.max(0, limit);
        Map<String, Long> counts = transactionRepository.findAll().stream()
                .filter(this::isFlagged)
                .filter(t -> t.getMerchantId() != null && !t.getMerchantId().isBlank())
                .collect(Collectors.groupingBy(Transaction::getMerchantId, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int compare = Long.compare(right.getValue(), left.getValue());
                    return compare != 0 ? compare : left.getKey().compareTo(right.getKey());
                })
                .limit(safeLimit)
                .map(entry -> FraudMerchantCountResponse.builder()
                        .merchantId(entry.getKey())
                        .flaggedCount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<FraudScoreDistributionBucket> buildScoreDistribution() {
        List<Transaction> all = transactionRepository.findAll();
        long[] counts = new long[5];
        for (Transaction tx : all) {
            double score = tx.getFraudScore() == null ? 0.0 : tx.getFraudScore();
            score = Math.max(0.0, Math.min(score, 0.999999));
            int bucketIndex = Math.min((int) Math.floor(score * counts.length), counts.length - 1);
            counts[bucketIndex]++;
        }
        List<FraudScoreDistributionBucket> buckets = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            double lower = i * 0.2;
            double upper = i == counts.length - 1 ? 1.0 : (i + 1) * 0.2;
            buckets.add(FraudScoreDistributionBucket.builder()
                    .label(String.format("%.1f-%.1f", lower, upper))
                    .lowerBound(lower)
                    .upperBound(upper)
                    .count(counts[i])
                    .build());
        }
        return buckets;
    }

    private boolean isFlagged(Transaction tx) {
        Transaction.FraudVerdict verdict = tx.getFraudVerdict();
        return verdict == Transaction.FraudVerdict.FRAUD || verdict == Transaction.FraudVerdict.REVIEW;
    }
}