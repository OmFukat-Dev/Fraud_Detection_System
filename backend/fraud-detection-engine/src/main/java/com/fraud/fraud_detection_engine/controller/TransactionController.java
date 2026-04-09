package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import com.fraud.fraud_detection_engine.dto.TransactionResponse;
import com.fraud.fraud_detection_engine.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Transaction Management", description = "APIs for submitting and retrieving payment transactions")
@SecurityRequirement(name = "bearer-jwt")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Submit a new payment transaction for fraud analysis.
     * POST /api/v1/transactions
     */
    @Operation(
            summary = "Submit transaction for fraud analysis",
            description = "Submit a new payment transaction which will be analyzed in real-time for fraud detection using rule-based and ML scoring"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Transaction submitted successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transaction data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            )
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Parameter(description = "Transaction details for fraud analysis", required = true)
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transaction submission for user: {}", request.getUserId());
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get all transactions for a specific user.
     * GET /api/v1/transactions/user/{userId}
     */
    @Operation(
            summary = "Get user transactions",
            description = "Retrieve all transactions for a specific user with pagination support"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @Parameter(description = "User ID to retrieve transactions for", required = true)
            @PathVariable String userId) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByUser(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get all flagged (fraud) transactions.
     * GET /api/v1/transactions/flagged
     */
    @Operation(
            summary = "Get flagged transactions",
            description = "Retrieve all transactions marked as FRAUD for review and investigation"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Flagged transactions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            )
    })
    @GetMapping("/flagged")
    public ResponseEntity<List<TransactionResponse>> getFlaggedTransactions() {
        List<TransactionResponse> flagged = transactionService.getFlaggedTransactions();
        return ResponseEntity.ok(flagged);
    }

    /**
     * Get a single transaction by its unique transactionId, including full fraud analysis details.
     * GET /api/v1/transactions/{transactionId}
     */
    @Operation(
            summary = "Get transaction by ID",
            description = "Retrieve detailed fraud analysis for a specific transaction including triggered rules and ML scores"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found"
            )
    })
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @Parameter(description = "Unique transaction ID", required = true)
            @PathVariable String transactionId) {
        log.info("Fetching transaction [{}]", transactionId);
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response);
    }
}
