
package com.fraud.fraud_detection_engine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SecurityThrottleService {

 private static final String API_RATE_PREFIX = "rate:api:";
 private static final String LOGIN_RATE_PREFIX = "rate:login:";
 private static final String LOGIN_FAILURE_PREFIX = "auth:fail:";
 private static final String LOGIN_LOCK_PREFIX = "auth:lock:";

 private final StringRedisTemplate stringRedisTemplate;

 @Value("${app.rate-limit.api.capacity}")
 private long apiCapacity;

 @Value("${app.rate-limit.api.refill-period-seconds}")
 private long apiRefillPeriodSeconds;

 @Value("${app.rate-limit.login.capacity}")
 private long loginCapacity;

 @Value("${app.rate-limit.login.refill-period-seconds}")
 private long loginRefillPeriodSeconds;

 @Value("${app.login.bruteforce.failure-threshold}")
 private long loginFailureThreshold;

 @Value("${app.login.bruteforce.failure-window-minutes}")
 private long loginFailureWindowMinutes;

 @Value("${app.login.bruteforce.lockout-minutes}")
 private long loginLockoutMinutes;

 public RateLimitDecision checkApiRequest(String clientKey) {
 return consume(API_RATE_PREFIX + clientKey, apiCapacity, apiRefillPeriodSeconds);
 }

 public RateLimitDecision checkLoginRequest(String clientKey) {
 return consume(LOGIN_RATE_PREFIX + clientKey, loginCapacity, loginRefillPeriodSeconds);
 }

 public LoginAttemptStatus assertLoginAllowed(String clientKey) {
 String lockKey = loginLockKey(clientKey);
 Boolean locked = stringRedisTemplate.hasKey(lockKey);
 if (Boolean.TRUE.equals(locked)) {
 return new LoginAttemptStatus(true, currentRemainingAttempts(clientKey), remainingLockoutSeconds(lockKey));
 }
 return new LoginAttemptStatus(false, currentRemainingAttempts(clientKey), 0L);
 }

 public LoginAttemptStatus recordLoginFailure(String clientKey) {
 String failureKey = loginFailureKey(clientKey);
 Long failures = stringRedisTemplate.opsForValue().increment(failureKey);
 if (failures == null) {
 failures = 1L;
 }
 stringRedisTemplate.expire(failureKey, Duration.ofMinutes(loginFailureWindowMinutes));

 long remainingAttempts = loginFailureThreshold - failures;
 if (remainingAttempts < 0L) {
 remainingAttempts = 0L;
 }

 if (failures >= loginFailureThreshold) {
 stringRedisTemplate.delete(failureKey);
 String lockKey = loginLockKey(clientKey);
 stringRedisTemplate.opsForValue().set(lockKey, "locked", Duration.ofMinutes(loginLockoutMinutes));
 return new LoginAttemptStatus(true, 0L, Duration.ofMinutes(loginLockoutMinutes).toSeconds());
 }

 return new LoginAttemptStatus(false, remainingAttempts, 0L);
 }

 public void clearLoginFailures(String clientKey) {
 stringRedisTemplate.delete(loginFailureKey(clientKey));
 stringRedisTemplate.delete(loginLockKey(clientKey));
 }

 private RateLimitDecision consume(String key, long capacity, long refillPeriodSeconds) {
 Long count = stringRedisTemplate.opsForValue().increment(key);
 if (count == null) {
 count = 1L;
 }
 if (count == 1L) {
 stringRedisTemplate.expire(key, Duration.ofSeconds(refillPeriodSeconds));
 }

 long remaining = capacity - count;
 if (remaining >= 0L) {
 return new RateLimitDecision(true, remaining, 0L);
 }

 long retryAfterSeconds = remainingTtlSeconds(key, refillPeriodSeconds);
 return new RateLimitDecision(false, 0L, retryAfterSeconds);
 }

 private long currentRemainingAttempts(String clientKey) {
 String rawCount = stringRedisTemplate.opsForValue().get(loginFailureKey(clientKey));
 if (rawCount == null) {
 return loginFailureThreshold;
 }
 try {
 long count = Long.parseLong(rawCount);
 long remaining = loginFailureThreshold - count;
 if (remaining < 0L) {
 return 0L;
 }
 return remaining;
 } catch (NumberFormatException ex) {
 return loginFailureThreshold;
 }
 }

 private long remainingLockoutSeconds(String lockKey) {
 Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
 if (ttl == null) {
 return Duration.ofMinutes(loginLockoutMinutes).toSeconds();
 }
 if (ttl < 0L) {
 return Duration.ofMinutes(loginLockoutMinutes).toSeconds();
 }
 return ttl;
 }

 private long remainingTtlSeconds(String key, long fallbackSeconds) {
 Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
 if (ttl == null) {
 return fallbackSeconds;
 }
 if (ttl < 0L) {
 return fallbackSeconds;
 }
 return ttl;
 }

 private String loginFailureKey(String clientKey) {
 return LOGIN_FAILURE_PREFIX + clientKey;
 }

 private String loginLockKey(String clientKey) {
 return LOGIN_LOCK_PREFIX + clientKey;
 }

 public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterSeconds) {
 }

 public record LoginAttemptStatus(boolean locked, long remainingAttempts, long lockoutSeconds) {
 }
}
