package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.MlPredictionResponse;
import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MlScoringService {

    @Value("${app.ml.enabled:true}")
    private boolean enabled;

    @Value("${app.ml.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${app.ml.timeout-ms:500}")
    private long timeoutMs;

    @Value("${app.ml.circuit-breaker.failure-threshold:3}")
    private int circuitBreakerFailureThreshold;

    @Value("${app.ml.circuit-breaker.open-duration-ms:30000}")
    private long circuitBreakerOpenDurationMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntilEpochMs = new AtomicLong(0L);
    private final RestTemplate restTemplate;

    public MlScoringService(RestTemplateBuilder builder,
                            @Value("${app.ml.timeout-ms:500}") long timeoutMs) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public Optional<MlPredictionResponse> score(TransactionRequest request,
                                               long velocityCount,
                                               boolean geoAnomaly,
                                               double geoDistanceKm,
                                               double deviceAgeDays,
                                               boolean isNewDevice) {
        if (!enabled) {
            return Optional.empty();
        }

    if (isCircuitOpen()) {
        log.warn("ML circuit breaker open -- skipping ML scoring and using rule-only fallback");
        return Optional.empty();
    }

    try {
        String url = baseUrl.endsWith("/") ? baseUrl + "predict" : baseUrl + "/predict";

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", request.getAmount());
        payload.put("currency", request.getCurrency());
        payload.put("hour_of_day", LocalDateTime.now().getHour());
        payload.put("velocity_count", velocityCount);
        payload.put("geo_anomaly", geoAnomaly);
        payload.put("geo_distance_km", geoDistanceKm);
        payload.put("device_age_days", deviceAgeDays);
        payload.put("is_new_device", isNewDevice);
        payload.put("merchant_id", request.getMerchantId());
        payload.put("merchant_category", request.getMerchantCategory());

        ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            registerFailure("status=" + response.getStatusCode());
            return Optional.empty();
        }

        Object prob = response.getBody().get("fraudProbability");
        Object version = response.getBody().get("modelVersion");
        if (prob == null) {
            registerFailure("missing fraudProbability");
            return Optional.empty();
        }

        Double probability = Double.valueOf(prob.toString());
        String modelVersion = version != null ? version.toString() : "unknown";
        resetCircuitBreaker();
        log.debug("ML scoring succeeded -- modelVersion={} fraudProbability={}", modelVersion, probability);
        return Optional.of(new MlPredictionResponse(probability, modelVersion));

    } catch (RestClientException e) {
        registerFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
        return Optional.empty();
    } catch (Exception e) {
        registerFailure("unexpected response: " + e.getMessage());
        return Optional.empty();
    }
    }

    private boolean isCircuitOpen() {
        return Instant.now().toEpochMilli() < circuitOpenUntilEpochMs.get();
    }

    private void resetCircuitBreaker() {
        consecutiveFailures.set(0);
        circuitOpenUntilEpochMs.set(0L);
    }

    private void registerFailure(String reason) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("ML scoring unavailable: {} (consecutiveFailures={})", reason, failures);

        if (failures >= circuitBreakerFailureThreshold) {
            long openUntil = Instant.now().toEpochMilli() + circuitBreakerOpenDurationMs;
            circuitOpenUntilEpochMs.set(openUntil);
            consecutiveFailures.set(0);
            log.warn("ML circuit breaker opened until {}", Instant.ofEpochMilli(openUntil));
        }
    }
}
