package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import com.fraud.fraud_detection_engine.dto.TransactionResponse;
import com.fraud.fraud_detection_engine.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Submit a new payment transaction for fraud analysis.
     * POST /api/v1/transactions
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transaction submission for user: {}", request.getUserId());
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get all transactions for a specific user.
     * GET /api/v1/transactions/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @PathVariable String userId) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByUser(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get all flagged (fraud) transactions.
     * GET /api/v1/transactions/flagged
     */
    @GetMapping("/flagged")
    public ResponseEntity<List<TransactionResponse>> getFlaggedTransactions() {
        List<TransactionResponse> flagged = transactionService.getFlaggedTransactions();
        return ResponseEntity.ok(flagged);
    }

    /**
     * Get a single transaction by its unique transactionId, including full fraud analysis details.
     * GET /api/v1/transactions/{transactionId}
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable String transactionId) {
        log.info("Fetching transaction [{}]", transactionId);
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response);
    }
}
