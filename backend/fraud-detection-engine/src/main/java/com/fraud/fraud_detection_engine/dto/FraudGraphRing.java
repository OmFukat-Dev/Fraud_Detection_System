package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudGraphRing {
    private String ringId;
    private String summary;
    private int nodeCount;
    private int userCount;
    private int sharedEntityCount;
    private int transactionCount;
    private int flaggedTransactionCount;
    private double averageFraudScore;
    private List<String> nodes;
    private List<String> transactionIds;
}