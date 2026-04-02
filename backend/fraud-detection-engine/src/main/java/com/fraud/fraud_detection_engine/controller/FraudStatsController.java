package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.FraudStatsResponse;
import com.fraud.fraud_detection_engine.dto.FraudTimeseriesPoint;
import com.fraud.fraud_detection_engine.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing aggregate fraud statistics.
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudStatsController {

    private final TransactionService transactionService;

    @GetMapping("/stats")
    public ResponseEntity<FraudStatsResponse> getFraudStats() {
        log.info("Fetching fraud statistics");
        FraudStatsResponse stats = transactionService.getFraudStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/timeseries")
    public ResponseEntity<List<FraudTimeseriesPoint>> getTimeseries(
            @RequestParam(defaultValue = "6") int days) {
        return ResponseEntity.ok(transactionService.getFraudTimeseries(days));
    }
}
