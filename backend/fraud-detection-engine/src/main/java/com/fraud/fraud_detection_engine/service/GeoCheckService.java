package com.fraud.fraud_detection_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Detects geographical anomalies by comparing the current transaction location
 * with the user's last known location stored in Redis.
 *
 * <p>An anomaly is detected when:
 * <ul>
 *   <li>The Haversine distance exceeds {@code geoAnomalyDistanceKm}, AND</li>
 *   <li>The time elapsed since the last transaction is less than {@code geoAnomalyTimeMinutes}.</li>
 * </ul>
 * This catches "impossible travel" — e.g. two transactions 600 km apart within 10 minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoCheckService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.fraud.rules.geo-anomaly-distance-km:500}")
    private double geoAnomalyDistanceKm;

    @Value("${app.fraud.rules.geo-anomaly-time-minutes:30}")
    private int geoAnomalyTimeMinutes;

    private static final String GEO_KEY_PREFIX = "geo:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Checks whether the current transaction constitutes an impossible-travel anomaly,
     * then updates Redis with the current location.
     *
     * @param userId    the user making the transaction
     * @param latitude  current transaction latitude
     * @param longitude current transaction longitude
     * @return true if an impossible-travel anomaly is detected
     */
    public boolean isGeoAnomaly(String userId, double latitude, double longitude) {
        String key = GEO_KEY_PREFIX + userId;
        try {
            String stored = redisTemplate.opsForValue().get(key);
            boolean anomaly = false;

            if (stored != null) {
                String[] parts = stored.split(",");
                if (parts.length == 3) {
                    double lastLat = Double.parseDouble(parts[0]);
                    double lastLon = Double.parseDouble(parts[1]);
                    LocalDateTime lastTime = LocalDateTime.parse(parts[2], FORMATTER);

                    double distanceKm = haversineDistanceKm(lastLat, lastLon, latitude, longitude);
                    long minutesElapsed = Duration.between(lastTime, LocalDateTime.now()).toMinutes();

                    log.debug("Geo check for user [{}]: distance={}km, elapsed={}min (thresholds: {}km / {}min)",
                            userId, String.format("%.1f", distanceKm), minutesElapsed,
                            geoAnomalyDistanceKm, geoAnomalyTimeMinutes);

                    anomaly = distanceKm > geoAnomalyDistanceKm && minutesElapsed < geoAnomalyTimeMinutes;
                    if (anomaly) {
                        log.warn("Geo anomaly detected for user [{}]! Distance={}km in {}min",
                                userId, String.format("%.1f", distanceKm), minutesElapsed);
                    }
                }
            }

            // Update stored location with current position and timestamp
            String newValue = latitude + "," + longitude + "," + LocalDateTime.now().format(FORMATTER);
            // Keep the geo entry for 24 hours (a transaction wouldn't matter beyond that)
            redisTemplate.opsForValue().set(key, newValue, Duration.ofHours(24));

            return anomaly;

        } catch (Exception e) {
            log.warn("Redis geo check failed for user [{}]: {}. Skipping geo rule.", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the great-circle distance between two points on Earth using the Haversine formula.
     *
     * @param lat1 latitude of the first point (degrees)
     * @param lon1 longitude of the first point (degrees)
     * @param lat2 latitude of the second point (degrees)
     * @param lon2 longitude of the second point (degrees)
     * @return distance in kilometres
     */
    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
