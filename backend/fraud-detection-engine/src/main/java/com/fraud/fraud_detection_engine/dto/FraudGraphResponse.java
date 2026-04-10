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
public class FraudGraphResponse {
    private long totalTransactions;
    private long totalNodes;
    private long totalEdges;
    private long totalComponents;
    private long fraudRingsDetected;
    private long sharedDevices;
    private long sharedIps;
    private long sharedMerchants;
    private long sharedLocations;
    private List<FraudGraphRing> rings;
    private List<FraudGraphPattern> patterns;
    private List<FraudGraphChain> chains;
}