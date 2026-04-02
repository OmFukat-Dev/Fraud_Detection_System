package com.fraud.fraud_detection_engine.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudTimeseriesPoint {
    private String date; // yyyy-MM-dd
    private long total;
    private long fraud;
    private long review;
    private long allow;
    private double fraudRate;
}
