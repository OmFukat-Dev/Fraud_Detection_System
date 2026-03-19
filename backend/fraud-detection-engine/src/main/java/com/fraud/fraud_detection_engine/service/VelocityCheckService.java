package com.fraud.fraud_detection_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Tracks transaction velocity per user using Redis.
 *
 * <p>Uses a sliding-window approach: each call increments a Redis counter
 * keyed by userId, and the TTL is refreshed to {@code velocityWindowMinutes}.
 * The counter value represents how many transactions the user made within
 * the last window period.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityCheckService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.fraud.rules.velocity-window-minutes:5}")
    private int velocityWindowMinutes;

    private static final String VELOCITY_KEY_PREFIX = "velocity:";

    /**
     * Records a new transaction for the given userId and returns the current
     * transaction count within the sliding window.
     *
     * @param userId the user who made the transaction
     * @return count of transactions in the current window (including this one)
     */
    public long recordAndGetCount(String userId) {
        String key = VELOCITY_KEY_PREFIX + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // Reset TTL on every increment to implement a sliding window
            redisTemplate.expire(key, Duration.ofMinutes(velocityWindowMinutes));
            log.debug("Velocity check: user [{}] → {} txns in the last {} minutes",
                    userId, count, velocityWindowMinutes);
            return count != null ? count : 1L;
        } catch (Exception e) {
            log.warn("Redis velocity check failed for user [{}]: {}. Defaulting to 1.", userId, e.getMessage());
            return 1L;
        }
    }

    /**
     * Returns the current velocity count for a user without incrementing.
     * Useful for read-only checks.
     *
     * @param userId the user to check
     * @return current count or 0 if no entry exists
     */
    public long getCurrentCount(String userId) {
        String key = VELOCITY_KEY_PREFIX + userId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.warn("Redis read failed for velocity key [{}]: {}", key, e.getMessage());
            return 0L;
        }
    }
}
