package com.fraud.fraud_detection_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tracks first-seen timestamps for user devices and returns device age in days.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceAgeService {

    private final StringRedisTemplate redisTemplate;

    private static final String DEVICE_FIRST_SEEN_PREFIX = "device:first_seen:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Returns device age in days. If the device has never been seen, it is registered
     * and 0.0 is returned.
     */
    public double getDeviceAgeDays(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0.0;
        }

        String key = DEVICE_FIRST_SEEN_PREFIX + userId + ":" + deviceId;
        try {
            String stored = redisTemplate.opsForValue().get(key);
            if (stored == null) {
                String now = LocalDateTime.now().format(FORMATTER);
                redisTemplate.opsForValue().set(key, now, Duration.ofDays(90));
                return 0.0;
            }

            LocalDateTime firstSeen = LocalDateTime.parse(stored, FORMATTER);
            long days = Duration.between(firstSeen, LocalDateTime.now()).toDays();
            return Math.max(0.0, (double) days);

        } catch (Exception e) {
            log.warn("Redis device age lookup failed for user [{}]: {}", userId, e.getMessage());
            return 0.0;
        }
    }
}
