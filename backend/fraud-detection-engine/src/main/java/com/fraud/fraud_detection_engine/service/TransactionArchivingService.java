package com.fraud.fraud_detection_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionArchivingService {

    private final JdbcTemplate jdbcTemplate;
    private final ArchivingConfigService configService;

    @Scheduled(cron = "#{@archivingConfigService.getArchiveSchedule()}")
    @Transactional
    public void performScheduledArchive() {
        if (!configService.isArchiveEnabled()) {
            log.debug("Archiving is disabled, skipping scheduled run");
            return;
        }

        log.info("Starting scheduled transaction archiving process");
        
        String archiveRunId = UUID.randomUUID().toString();
        int daysOld = configService.getArchiveDaysOld();
        int batchSize = configService.getArchiveBatchSize();
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "CALL archive_transactions(?, ?, ?)",
                daysOld, batchSize, archiveRunId
            );
            
            int archivedCount = (Integer) result.get("archived_transactions");
            log.info("Successfully archived {} transactions in run {}", archivedCount, archiveRunId);
            
        } catch (Exception e) {
            log.error("Failed to archive transactions in run {}", archiveRunId, e);
            
            // Update audit log with failure
            jdbcTemplate.update(
                "UPDATE archiving_audit_log SET status = 'FAILED', error_message = ? WHERE archive_run_id = ?",
                e.getMessage(), archiveRunId
            );
        }
    }

    @Scheduled(cron = "0 3 * * SUN") // Weekly on Sunday at 3 AM
    @Transactional
    public void performScheduledCleanup() {
        log.info("Starting scheduled archive cleanup process");
        
        int cleanupDaysOld = configService.getCleanupArchiveDays();
        int batchSize = configService.getArchiveBatchSize();
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "CALL cleanup_old_archive(?, ?)",
                cleanupDaysOld, batchSize
            );
            
            int deletedCount = (Integer) result.get("deleted_transactions");
            log.info("Successfully cleaned up {} old archived transactions", deletedCount);
            
        } catch (Exception e) {
            log.error("Failed to cleanup old archived transactions", e);
        }
    }

    public Map<String, Object> getArchiveStatistics() {
        try {
            return jdbcTemplate.queryForMap("SELECT get_archive_statistics() as stats");
        } catch (Exception e) {
            log.error("Failed to get archive statistics", e);
            return Map.of();
        }
    }

    @Transactional
    public void manualArchive(int daysOld, int batchSize) {
        log.info("Starting manual archive for transactions older than {} days", daysOld);
        
        String archiveRunId = UUID.randomUUID().toString();
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "CALL archive_transactions(?, ?, ?)",
                daysOld, batchSize, archiveRunId
            );
            
            int archivedCount = (Integer) result.get("archived_transactions");
            log.info("Manual archive completed: {} transactions archived", archivedCount);
            
        } catch (Exception e) {
            log.error("Manual archive failed", e);
            throw new RuntimeException("Manual archive failed", e);
        }
    }

    @Transactional
    public void manualCleanup(int daysOld, int batchSize) {
        log.info("Starting manual cleanup for archives older than {} days", daysOld);
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "CALL cleanup_old_archive(?, ?)",
                daysOld, batchSize
            );
            
            int deletedCount = (Integer) result.get("deleted_transactions");
            log.info("Manual cleanup completed: {} transactions deleted", deletedCount);
            
        } catch (Exception e) {
            log.error("Manual cleanup failed", e);
            throw new RuntimeException("Manual cleanup failed", e);
        }
    }
}
