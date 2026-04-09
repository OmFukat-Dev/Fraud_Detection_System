package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated merchant fraud count for analytics charts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudMerchantCountResponse {

 private String merchantId;
 private long flaggedCount;
}
