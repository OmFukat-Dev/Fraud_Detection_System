package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary statistics DTO returned by GET /api/v1/fraud/stats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudStatsResponse {

    private long totalTransactions;
    private long totalFraud;
    private long totalReview;
    private long totalAllow;
    private long totalPending;
    private double averageFraudScore;
    private double fraudRate; // fraud / total (as %)
}
