package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.service.ArchivingConfigService;
import com.fraud.fraud_detection_engine.service.TransactionArchivingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/archiving")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction Archiving", description = "Admin APIs for managing transaction archiving and cleanup")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class ArchivingController {

    private final TransactionArchivingService archivingService;
    private final ArchivingConfigService configService;

    @Operation(
            summary = "Get archive statistics",
            description = "Retrieve comprehensive statistics about transaction archiving"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Archive statistics retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getArchiveStatistics() {
        Map<String, Object> stats = archivingService.getArchiveStatistics();
        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Get archiving configuration",
            description = "Retrieve current archiving configuration settings"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Configuration retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getArchivingConfig() {
        Map<String, Object> config = configService.getAllConfig();
        return ResponseEntity.ok(config);
    }

    @Operation(
            summary = "Update archiving configuration",
            description = "Update specific archiving configuration parameters"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Configuration updated successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @PutMapping("/config/{configKey}")
    public ResponseEntity<Void> updateArchivingConfig(
            @Parameter(description = "Configuration key", required = true)
            @PathVariable String configKey,
            @Parameter(description = "Configuration value", required = true)
            @RequestParam String configValue) {
        
        configService.updateConfig(configKey, configValue);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Trigger manual archive",
            description = "Manually trigger transaction archiving for specified age"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Manual archive triggered successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @PostMapping("/archive")
    public ResponseEntity<String> triggerManualArchive(
            @Parameter(description = "Archive transactions older than this many days", required = false)
            @RequestParam(defaultValue = "365") int daysOld,
            @Parameter(description = "Batch size for each operation", required = false)
            @RequestParam(defaultValue = "1000") int batchSize) {
        
        log.info("Admin triggered manual archive: daysOld={}, batchSize={}", daysOld, batchSize);
        archivingService.manualArchive(daysOld, batchSize);
        
        return ResponseEntity.ok("Manual archive initiated for transactions older than " + daysOld + " days");
    }

    @Operation(
            summary = "Trigger manual cleanup",
            description = "Manually trigger cleanup of old archived transactions"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Manual cleanup triggered successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @PostMapping("/cleanup")
    public ResponseEntity<String> triggerManualCleanup(
            @Parameter(description = "Clean up archives older than this many days", required = false)
            @RequestParam(defaultValue = "2555") int daysOld,
            @Parameter(description = "Batch size for each operation", required = false)
            @RequestParam(defaultValue = "1000") int batchSize) {
        
        log.info("Admin triggered manual cleanup: daysOld={}, batchSize={}", daysOld, batchSize);
        archivingService.manualCleanup(daysOld, batchSize);
        
        return ResponseEntity.ok("Manual cleanup initiated for archives older than " + daysOld + " days");
    }

    @Operation(
            summary = "Enable/disable archiving",
            description = "Enable or disable automatic transaction archiving"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Archiving status updated successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @PutMapping("/enabled")
    public ResponseEntity<String> setArchiveEnabled(
            @Parameter(description = "Enable archiving", required = true)
            @RequestParam boolean enabled) {
        
        configService.updateConfig("archive_enabled", String.valueOf(enabled));
        String status = enabled ? "enabled" : "disabled";
        log.info("Admin set archiving to: {}", status);
        
        return ResponseEntity.ok("Transaction archiving " + status);
    }
}
