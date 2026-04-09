package com.fraud.fraud_detection_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of evaluating a single fraud detection rule.
 * Each rule contributes a score between 0.0 and its defined maximum weight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {

    /** Human-readable name of the rule that was evaluated. */
    private String ruleName;

    /** Whether the rule was triggered (i.e., suspicious behaviour detected). */
    private boolean triggered;

    /** Score contribution to add to the total fraud score (0.0 if not triggered). */
    private double scoreContribution;

    /** Human-readable explanation for why the rule triggered or did not trigger. */
    private String explanation;

    /**
     * Convenience factory for a triggered rule result.
     */
    public static RuleResult triggered(String ruleName, double scoreContribution) {
        return triggered(ruleName, scoreContribution, null);
    }

    public static RuleResult triggered(String ruleName, double scoreContribution, String explanation) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .triggered(true)
                .scoreContribution(scoreContribution)
                .explanation(explanation)
                .build();
    }

    /**
     * Convenience factory for a clean (not-triggered) rule result.
     */
    public static RuleResult clean(String ruleName) {
        return clean(ruleName, null);
    }

    public static RuleResult clean(String ruleName, String explanation) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .triggered(false)
                .scoreContribution(0.0)
                .explanation(explanation)
                .build();
    }
}