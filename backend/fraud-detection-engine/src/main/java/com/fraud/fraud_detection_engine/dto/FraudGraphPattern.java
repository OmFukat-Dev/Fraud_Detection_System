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
public class FraudGraphPattern {
    private String patternType;
    private String label;
    private String description;
    private int userCount;
    private int transactionCount;
    private int flaggedTransactionCount;
    private List<String> users;
    private List<String> transactionIds;
}