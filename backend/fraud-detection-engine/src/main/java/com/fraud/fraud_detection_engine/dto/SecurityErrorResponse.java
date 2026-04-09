package com.fraud.fraud_detection_engine.dto;

public record SecurityErrorResponse(
        String error,
        String message,
        String clientIp,
        String path,
        long retryAfterSeconds,
        long remainingAttempts) {
}
