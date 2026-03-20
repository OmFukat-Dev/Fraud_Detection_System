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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MlScoringService {

    @Value("${app.ml.enabled:true}")
    private boolean enabled;

    @Value("${app.ml.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${app.ml.timeout-ms:500}")
    private long timeoutMs;

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
                                               boolean isNewDevice) {
        if (!enabled) {
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
            payload.put("is_new_device", isNewDevice);
            payload.put("merchant_id", request.getMerchantId());

            ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("ML scoring failed with status {}", response.getStatusCode());
                return Optional.empty();
            }

            Object prob = response.getBody().get("fraudProbability");
            Object version = response.getBody().get("modelVersion");
            if (prob == null) {
                return Optional.empty();
            }

            Double probability = Double.valueOf(prob.toString());
            String modelVersion = version != null ? version.toString() : null;
            return Optional.of(new MlPredictionResponse(probability, modelVersion));

        } catch (RestClientException e) {
            log.warn("ML scoring unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
