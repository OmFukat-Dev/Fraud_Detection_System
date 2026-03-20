package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlPredictionResponse {
    private Double fraudProbability;
    private String modelVersion;
}
