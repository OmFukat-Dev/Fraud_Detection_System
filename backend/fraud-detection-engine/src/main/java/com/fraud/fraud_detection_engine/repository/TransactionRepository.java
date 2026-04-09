package com.fraud.fraud_detection_engine.repository;

import com.fraud.fraud_detection_engine.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Transaction> findByUserIdAndCreatedAtAfter(String userId, LocalDateTime since);

    List<Transaction> findByFraudVerdictOrderByCreatedAtDesc(Transaction.FraudVerdict verdict);

    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime since);

    long countByStatus(Transaction.TransactionStatus status);
}
