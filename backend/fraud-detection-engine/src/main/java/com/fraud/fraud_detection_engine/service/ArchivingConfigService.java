package com.fraud.fraud_detection_engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ArchivingConfigService {

    private final JdbcTemplate jdbcTemplate;

    public ArchivingConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isArchiveEnabled() {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM archiving_config WHERE config_key = 'archive_enabled'",
                String.class
            );
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            log.warn("Could not determine archive enabled status, defaulting to false", e);
            return false;
        }
    }

    public int getArchiveDaysOld() {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM archiving_config WHERE config_key = 'archive_days_old'",
                String.class
            );
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Could not get archive days old, defaulting to 365", e);
            return 365;
        }
    }

    public int getArchiveBatchSize() {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM archiving_config WHERE config_key = 'archive_batch_size'",
                String.class
            );
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Could not get archive batch size, defaulting to 1000", e);
            return 1000;
        }
    }

    public String getArchiveSchedule() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT config_value FROM archiving_config WHERE config_key = 'archive_schedule'",
                String.class
            );
        } catch (Exception e) {
            log.warn("Could not get archive schedule, defaulting to daily at 2 AM", e);
            return "0 2 * * *";
        }
    }

    public int getCleanupArchiveDays() {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM archiving_config WHERE config_key = 'cleanup_archive_days'",
                String.class
            );
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Could not get cleanup archive days, defaulting to 2555 (7 years)", e);
            return 2555;
        }
    }

    public void updateConfig(String configKey, String configValue) {
        jdbcTemplate.update(
            "INSERT INTO archiving_config (config_key, config_value, description) " +
            "VALUES (?, ?, '') " +
            "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = CURRENT_TIMESTAMP",
            configKey, configValue
        );
        log.info("Updated archiving config: {} = {}", configKey, configValue);
    }

    public Map<String, Object> getAllConfig() {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT config_key, config_value FROM archiving_config"
            );
        } catch (Exception e) {
            log.error("Failed to get all archiving config", e);
            return Map.of();
        }
    }
}
