package com.fraud.fraud_detection_engine.dto;

import com.fraud.fraud_detection_engine.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated result produced by the FraudRuleEngine after evaluating all rules.
 * This is the internal DTO passed between consumer ? service ? alert service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {

    /** The unique ID of the transaction that was analyzed. */
    private String transactionId;

    /**
     * Aggregated fraud probability score (0.0 ? 1.0).
     * This is the final score after combining rule and ML signals.
     */
    private double fraudScore;

    /**
     * Rule-based fraud probability score (0.0 ? 1.0).
     * Used as a baseline and fallback if ML scoring is unavailable.
     */
    private Double ruleScore;

    /**
     * ML-based fraud probability score (0.0 ? 1.0), if available.
     */
    private Double mlScore;

    /**
     * Model version used for ML scoring, if available.
     */
    private String mlModelVersion;

    /**
     * Final verdict based on the fraud score:
     * <ul>
     *   <li>0.0 ? 0.4  ?  ALLOW</li>
     *   <li>0.4 ? 0.7  ?  REVIEW</li>
     *   <li>0.7 ? 1.0  ?  FRAUD</li>
     * </ul>
     */
    private Transaction.FraudVerdict fraudVerdict;

    /** Ordered list of individual rule results that were evaluated. */
    private List<RuleResult> ruleResults;

    /**
     * Top explainability reasons used by the detail and analyst views.
     * Usually the strongest triggered rules plus contextual signals.
     */
    private List<RuleResult> explanationReasons;

    /**
     * Comma-separated names of all rules that were triggered.
     * Used to persist into the {@code triggered_rules} column.
     */
    private String triggeredRulesSummary;
}