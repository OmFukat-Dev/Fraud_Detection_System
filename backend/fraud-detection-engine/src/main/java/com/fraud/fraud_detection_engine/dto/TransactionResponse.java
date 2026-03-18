package com.fraud.fraud_detection_engine.dto;

import com.fraud.fraud_detection_engine.model.Transaction.FraudVerdict;
import com.fraud.fraud_detection_engine.model.Transaction.TransactionStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private Long id;
    private String transactionId;
    private String userId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private FraudVerdict fraudVerdict;
    private Double fraudScore;
    private String triggeredRules;
    private String message;
    private LocalDateTime createdAt;
}
