package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.FraudStatsResponse;
import com.fraud.fraud_detection_engine.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing aggregate fraud statistics.
 *
 * <p>Endpoint: {@code GET /api/v1/fraud/stats}
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudStatsController {

    private final TransactionService transactionService;

    /**
     * Returns aggregate fraud statistics across all transactions.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "totalTransactions": 150,
     *   "totalFraud": 12,
     *   "totalReview": 35,
     *   "totalAllow": 98,
     *   "totalPending": 5,
     *   "averageFraudScore": 0.2341,
     *   "fraudRate": 8.0
     * }
     * </pre>
     *
     * @return {@link FraudStatsResponse} with all aggregate counts and rates
     */
    @GetMapping("/stats")
    public ResponseEntity<FraudStatsResponse> getFraudStats() {
        log.info("Fetching fraud statistics");
        FraudStatsResponse stats = transactionService.getFraudStats();
        return ResponseEntity.ok(stats);
    }
}
