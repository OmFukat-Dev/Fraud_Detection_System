package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Score distribution bucket for fraud analytics charts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScoreDistributionBucket {

 private String label;
 private double lowerBound;
 private double upperBound;
 private long count;
}
