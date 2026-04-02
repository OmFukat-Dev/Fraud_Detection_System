package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudAnalysisResult;
import com.fraud.fraud_detection_engine.dto.MlPredictionResponse;
import com.fraud.fraud_detection_engine.dto.RuleResult;
import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.service.GeoCheckService.GeoCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core fraud detection engine that evaluates a transaction against 5 rules:
 * <ol>
 *   <li>High Amount -- large transaction value</li>
 *   <li>Velocity -- too many transactions in a short period</li>
 *   <li>Geo-Anomaly -- impossible travel between locations</li>
 *   <li>Blacklist -- blocked merchant ID or IP address</li>
 *   <li>New Device -- unrecognised device for this user</li>
 * </ol>
 *
 * <p>Each rule contributes a weighted score. The total is capped at 1.0 and
 * mapped to a {@link Transaction.FraudVerdict}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudRuleEngine {

    // Rule-specific services
    private final VelocityCheckService velocityCheckService;
    private final GeoCheckService geoCheckService;
    private final DeviceAgeService deviceAgeService;
    private final StringRedisTemplate redisTemplate;
    private final MlScoringService mlScoringService;

    // Score weights
    private static final double WEIGHT_HIGH_AMOUNT  = 0.35;
    private static final double WEIGHT_VELOCITY     = 0.30;
    private static final double WEIGHT_GEO_ANOMALY  = 0.20;
    private static final double WEIGHT_BLACKLIST     = 0.40;
    private static final double WEIGHT_NEW_DEVICE   = 0.15;

    // Verdict thresholds
    private static final double THRESHOLD_FRAUD     = 0.70;
    private static final double THRESHOLD_REVIEW    = 0.40;

    // Configurable rule parameters
    @Value("${app.fraud.rules.high-amount-threshold:50000}")
    private BigDecimal highAmountThreshold;

    @Value("${app.fraud.rules.velocity-max-count:5}")
    private int velocityMaxCount;

    @Value("#{'${app.fraud.rules.blacklisted-merchants:}'.split(',')}")
    private List<String> blacklistedMerchants;

    @Value("#{'${app.fraud.rules.blacklisted-ips:}'.split(',')}")
    private List<String> blacklistedIps;

    @Value("${app.ml.rule-weight:0.6}")
    private double ruleWeight;

    @Value("${app.ml.model-weight:0.4}")
    private double modelWeight;

    private static final String DEVICE_KEY_PREFIX = "devices:";

    // Public API

    /**
     * Evaluates all fraud detection rules for the given transaction and returns
     * an aggregated {@link FraudAnalysisResult}.
     *
     * @param transactionId unique ID of the transaction being analyzed
     * @param request       the transaction data
     * @return the analysis result including fraud score, verdict, and triggered rules
     */
    public FraudAnalysisResult analyze(String transactionId, TransactionRequest request) {
        log.info("Starting fraud analysis for transaction [{}] -- user [{}], amount [{}]",
                transactionId, request.getUserId(), request.getAmount());

        List<RuleResult> results = new ArrayList<>();

        // Evaluate all rules
        results.add(checkHighAmount(request));

        long velocityCount = velocityCheckService.recordAndGetCount(request.getUserId());
        results.add(checkVelocity(velocityCount));

        GeoCheckResult geoResult = checkGeoAnomaly(request, results);
        boolean geoAnomaly = geoResult.isAnomaly();
        double geoDistanceKm = geoResult.getDistanceKm();

        results.add(checkBlacklist(request));
        RuleResult newDeviceResult = checkNewDevice(request);
        results.add(newDeviceResult);

        double deviceAgeDays = deviceAgeService.getDeviceAgeDays(request.getUserId(), request.getDeviceId());

        // Aggregate rule-based score (capped at 1.0)
        double rawScore = results.stream()
                .mapToDouble(RuleResult::getScoreContribution)
                .sum();
        double ruleScore = Math.min(rawScore, 1.0);

        // ML scoring (optional)
        Optional<MlPredictionResponse> mlResult = mlScoringService.score(
                request,
                velocityCount,
                geoAnomaly,
                geoDistanceKm,
                deviceAgeDays,
                newDeviceResult.isTriggered()
        );

        Double mlScore = null;
        String mlModelVersion = null;
        double finalScore = ruleScore;

        if (mlResult.isPresent()) {
            mlScore = clampScore(mlResult.get().getFraudProbability());
            mlModelVersion = mlResult.get().getModelVersion();

            double weightSum = ruleWeight + modelWeight;
            if (weightSum > 0) {
                finalScore = (ruleWeight * ruleScore + modelWeight * mlScore) / weightSum;
            }
        }

        finalScore = clampScore(finalScore);

        // Determine verdict
        Transaction.FraudVerdict verdict = determineVerdict(finalScore);

        // Collect triggered rule names
        String triggeredSummary = results.stream()
                .filter(RuleResult::isTriggered)
                .map(RuleResult::getRuleName)
                .collect(Collectors.joining(", "));

        log.info("Fraud analysis complete for [{}]: score={} verdict={} ruleScore={} mlScore={} mlModelVersion={} triggeredRules=[{}]",
                transactionId,
                String.format("%.4f", finalScore),
                verdict,
                String.format("%.4f", ruleScore),
                mlScore != null ? String.format("%.4f", mlScore) : "N/A",
                mlModelVersion != null ? mlModelVersion : "RULE_ONLY",
                triggeredSummary.isEmpty() ? "NONE" : triggeredSummary);

        return FraudAnalysisResult.builder()
                .transactionId(transactionId)
                .fraudScore(finalScore)
                .ruleScore(ruleScore)
                .mlScore(mlScore)
                .mlModelVersion(mlModelVersion)
                .fraudVerdict(verdict)
                .ruleResults(results)
                .triggeredRulesSummary(triggeredSummary)
                .build();
    }

    // Private Rule Implementations

    /**
     * Rule 1: High Amount
     * Flags transactions whose amount exceeds the configured threshold.
     */
    private RuleResult checkHighAmount(TransactionRequest request) {
        boolean triggered = request.getAmount().compareTo(highAmountThreshold) > 0;
        if (triggered) {
            log.debug("HIGH_AMOUNT rule triggered: {} > {}", request.getAmount(), highAmountThreshold);
            return RuleResult.triggered("HIGH_AMOUNT", WEIGHT_HIGH_AMOUNT);
        }
        return RuleResult.clean("HIGH_AMOUNT");
    }

    /**
     * Rule 2: Velocity
     * Flags users who exceed the allowed transaction count within the sliding time window.
     */
    private RuleResult checkVelocity(long count) {
        boolean triggered = count > velocityMaxCount;
        if (triggered) {
            log.debug("VELOCITY rule triggered: {} txns in window (max: {})", count, velocityMaxCount);
            return RuleResult.triggered("VELOCITY", WEIGHT_VELOCITY);
        }
        return RuleResult.clean("VELOCITY");
    }

    /**
     * Rule 3: Geo-Anomaly (Impossible Travel)
     * Uses Haversine distance to detect physically impossible location changes.
     */
    private GeoCheckResult checkGeoAnomaly(TransactionRequest request, List<RuleResult> results) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            results.add(RuleResult.clean("GEO_ANOMALY"));
            return new GeoCheckResult(false, 0.0, -1L);
        }
        GeoCheckResult result = geoCheckService.checkGeo(
                request.getUserId(), request.getLatitude(), request.getLongitude());
        if (result.isAnomaly()) {
            results.add(RuleResult.triggered("GEO_ANOMALY", WEIGHT_GEO_ANOMALY));
        } else {
            results.add(RuleResult.clean("GEO_ANOMALY"));
        }
        return result;
    }

    /**
     * Rule 4: Blacklist
     * Flags transactions where the merchant ID or IP address is on the block-list.
     */
    private RuleResult checkBlacklist(TransactionRequest request) {
        boolean merchantBlocked = blacklistedMerchants.stream()
                .anyMatch(m -> m.equalsIgnoreCase(request.getMerchantId()));
        boolean ipBlocked = request.getIpAddress() != null && blacklistedIps.stream()
                .anyMatch(ip -> ip.equals(request.getIpAddress()));

        boolean triggered = merchantBlocked || ipBlocked;
        if (triggered) {
            log.debug("BLACKLIST rule triggered: merchant={} ip={}", merchantBlocked, ipBlocked);
            return RuleResult.triggered("BLACKLIST", WEIGHT_BLACKLIST);
        }
        return RuleResult.clean("BLACKLIST");
    }

    /**
     * Rule 5: New Device
     * Flags when a deviceId has never been seen for this user before.
     * Stores the device in a Redis Set so it becomes known on subsequent transactions.
     */
    private RuleResult checkNewDevice(TransactionRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            return RuleResult.clean("NEW_DEVICE");
        }
        String key = DEVICE_KEY_PREFIX + request.getUserId();
        try {
            // SADD returns 1 if element was new, 0 if it already existed
            Long added = redisTemplate.opsForSet().add(key, request.getDeviceId());
            // Keep device history for 90 days
            redisTemplate.expire(key, Duration.ofDays(90));
            boolean isNewDevice = added != null && added > 0;
            if (isNewDevice) {
                log.debug("NEW_DEVICE rule triggered: deviceId [{}] never seen for user [{}]",
                        request.getDeviceId(), request.getUserId());
                return RuleResult.triggered("NEW_DEVICE", WEIGHT_NEW_DEVICE);
            }
        } catch (Exception e) {
            log.warn("Redis device check failed for user [{}]: {}. Skipping new-device rule.",
                    request.getUserId(), e.getMessage());
        }
        return RuleResult.clean("NEW_DEVICE");
    }

    // Verdict Mapping

    private Transaction.FraudVerdict determineVerdict(double score) {
        if (score >= THRESHOLD_FRAUD) {
            return Transaction.FraudVerdict.FRAUD;
        } else if (score >= THRESHOLD_REVIEW) {
            return Transaction.FraudVerdict.REVIEW;
        } else {
            return Transaction.FraudVerdict.ALLOW;
        }
    }

    private double clampScore(double score) {
        if (score < 0.0) {
            return 0.0;
        }
        if (score > 1.0) {
            return 1.0;
        }
        return score;
    }
}
