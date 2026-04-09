-- transactions Archiving Strategy for Fraud Detection System
-- Migration: V2__Create_archiving_tables_and_procedures.sql

-- Create archive table structure (same as main transactions table)
CREATE TABLE IF NOT EXISTS transaction_archive (
    transaction_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    merchant_id VARCHAR(255),
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    device_id VARCHAR(255),
    ip_address VARCHAR(45),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    status ENUM('PENDING', 'COMPLETED', 'BLOCKED') NOT NULL,
    fraud_verdict ENUM('ALLOW', 'REVIEW', 'FRAUD') NOT NULL,
    fraud_score DECIMAL(5,4) NOT NULL,
    triggered_rules TEXT,
    rule_score DECIMAL(5,4),
    ml_score DECIMAL(5,4),
    ml_model_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archive_reason VARCHAR(50) NOT NULL,
    
    PRIMARY KEY (transaction_id),
    INDEX idx_archive_user_id (user_id),
    INDEX idx_archive_merchant_id (merchant_id),
    INDEX idx_archive_fraud_verdict (fraud_verdict),
    INDEX idx_archive_created_at (created_at),
    INDEX idx_archive_archived_at (archived_at),
    INDEX idx_archive_user_created (user_id, created_at),
    INDEX idx_archive_verdict_created (fraud_verdict, created_at),
    INDEX idx_archive_merchant_verdict (merchant_id, fraud_verdict),
    INDEX idx_archive_device_id (device_id),
    INDEX idx_archive_ip_address (ip_address),
    INDEX idx_archive_geo (latitude, longitude),
    INDEX idx_archive_status (status),
    INDEX idx_archive_reason (archive_reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create archiving configuration table
CREATE TABLE IF NOT EXISTS archiving_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default archiving configuration
INSERT INTO archiving_config (config_key, config_value, description) VALUES
('archive_enabled', 'true', 'Enable automatic transactions archiving'),
('archive_days_old', '365', 'Archive transactions older than this many days'),
('archive_batch_size', '1000', 'Number of transactions to archive in each batch'),
('archive_schedule', '0 2 * * *', 'Cron schedule for archiving (daily at 2 AM)'),
('cleanup_archive_days', '2555', 'Delete archived transactions after this many days (7 years)'),
('archive_allow_fraud', 'false', 'Whether to archive fraud transactions'),
('archive_allow_review', 'true', 'Whether to archive review transactions'),
('archive_allow_allow', 'true', 'Whether to archive allow transactions')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- Create archiving audit log
CREATE TABLE IF NOT EXISTS archiving_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_run_id VARCHAR(36) NOT NULL,
    run_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transactions_moved INT NOT NULL,
    archive_reason VARCHAR(50) NOT NULL,
    date_range_start DATE,
    date_range_end DATE,
    status ENUM('STARTED', 'COMPLETED', 'FAILED') NOT NULL,
    error_message TEXT,
    execution_time_seconds INT,
    INDEX idx_audit_run_id (archive_run_id),
    INDEX idx_audit_timestamp (run_timestamp),
    INDEX idx_audit_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create stored procedure for archiving transactions
DELIMITER //

CREATE PROCEDURE IF NOT EXISTS archive_transactions(
    IN p_days_old INT,
    IN p_batch_size INT,
    IN p_archive_run_id VARCHAR(36)
)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE archived_count INT DEFAULT 0;
    DECLARE total_archived INT DEFAULT 0;
    DECLARE start_time TIMESTAMP DEFAULT NOW(3);
    DECLARE date_start DATE;
    DECLARE date_end DATE;
    
    -- Calculate date range
    SET date_start = DATE_SUB(CURDATE(), INTERVAL p_days_old DAY);
    SET date_end = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
    
    -- Log archive start
    INSERT INTO archiving_audit_log (archive_run_id, transactions_moved, archive_reason, date_range_start, date_range_end, status)
    VALUES (p_archive_run_id, 0, 'SCHEDULED_ARCHIVE', date_start, date_end, 'STARTED');
    
    -- Archive ALLOW transactions
    archive_loop: LOOP
        -- Move transactions to archive table
        INSERT INTO transaction_archive (
            transaction_id, user_id, merchant_id, amount, currency, device_id, ip_address,
            latitude, longitude, status, fraud_verdict, fraud_score, triggered_rules,
            rule_score, ml_score, ml_model_version, created_at, updated_at, archive_reason
        )
        SELECT 
            t.transaction_id, t.user_id, t.merchant_id, t.amount, t.currency, t.device_id, t.ip_address,
            t.latitude, t.longitude, t.status, t.fraud_verdict, t.fraud_score, t.triggered_rules,
            t.rule_score, t.ml_score, t.ml_model_version, t.created_at, t.updated_at, 'SCHEDULED_ARCHIVE'
        FROM transactions t
        WHERE t.fraud_verdict = 'ALLOW'
        AND t.created_at < date_start
        LIMIT p_batch_size;
        
        SET archived_count = ROW_COUNT();
        SET total_archived = total_archived + archived_count;
        
        -- Delete archived transactions from main table
        DELETE FROM transactions 
        WHERE transaction_id IN (
            SELECT transaction_id FROM transaction_archive 
            WHERE archive_reason = 'SCHEDULED_ARCHIVE' 
            AND archived_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            LIMIT p_batch_size
        );
        
        -- Exit if no more records to process
        IF archived_count = 0 THEN
            LEAVE archive_loop;
        END IF;
        
        -- Safety check to prevent infinite loops
        IF total_archived > 100000 THEN
            LEAVE archive_loop;
        END IF;
    END LOOP;
    
    -- Archive REVIEW transactions if enabled
    IF (SELECT config_value FROM archiving_config WHERE config_key = 'archive_allow_review') = 'true' THEN
        SET archived_count = 0;
        
        review_loop: LOOP
            INSERT INTO transaction_archive (
                transaction_id, user_id, merchant_id, amount, currency, device_id, ip_address,
                latitude, longitude, status, fraud_verdict, fraud_score, triggered_rules,
                rule_score, ml_score, ml_model_version, created_at, updated_at, archive_reason
            )
            SELECT 
                t.transaction_id, t.user_id, t.merchant_id, t.amount, t.currency, t.device_id, t.ip_address,
                t.latitude, t.longitude, t.status, t.fraud_verdict, t.fraud_score, t.triggered_rules,
                t.rule_score, t.ml_score, t.ml_model_version, t.created_at, t.updated_at, 'SCHEDULED_ARCHIVE'
            FROM transactions t
            WHERE t.fraud_verdict = 'REVIEW'
            AND t.created_at < date_start
            LIMIT p_batch_size;
            
            SET archived_count = ROW_COUNT();
            SET total_archived = total_archived + archived_count;
            
            DELETE FROM transactions 
            WHERE transaction_id IN (
                SELECT transaction_id FROM transaction_archive 
                WHERE archive_reason = 'SCHEDULED_ARCHIVE' 
                AND fraud_verdict = 'REVIEW'
                AND archived_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
                LIMIT p_batch_size
            );
            
            IF archived_count = 0 THEN
                LEAVE review_loop;
            END IF;
        END LOOP;
    END IF;
    
    -- Update audit log with completion
    UPDATE archiving_audit_log 
    SET transactions_moved = total_archived,
        status = 'COMPLETED',
        execution_time_seconds = TIMESTAMPDIFF(SECOND, start_time, NOW(3))
    WHERE archive_run_id = p_archive_run_id
    AND run_timestamp = start_time;
    
    SELECT total_archived as archived_transactions;
END //

DELIMITER ;

-- Create procedure for cleaning up old archived transactions
DELIMITER //

CREATE PROCEDURE IF NOT EXISTS cleanup_old_archive(
    IN p_cleanup_days_old INT,
    IN p_batch_size INT
)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE deleted_count INT DEFAULT 0;
    DECLARE total_deleted INT DEFAULT 0;
    DECLARE cleanup_date DATE;
    
    SET cleanup_date = DATE_SUB(CURDATE(), INTERVAL p_cleanup_days_old DAY);
    
    cleanup_loop: LOOP
        DELETE FROM transaction_archive 
        WHERE archived_at < cleanup_date
        LIMIT p_batch_size;
        
        SET deleted_count = ROW_COUNT();
        SET total_deleted = total_deleted + deleted_count;
        
        IF deleted_count = 0 THEN
            LEAVE cleanup_loop;
        END IF;
        
        -- Safety check
        IF total_deleted > 100000 THEN
            LEAVE cleanup_loop;
        END IF;
    END LOOP;
    
    SELECT total_deleted as deleted_transactions;
END //

DELIMITER ;

-- Create function to get archiving statistics
DELIMITER //

CREATE FUNCTION IF NOT EXISTS get_archive_statistics() 
RETURNS JSON
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE json_result JSON;
    
    SELECT JSON_OBJECT(
        'total_transactions', (SELECT COUNT(*) FROM transactions),
        'total_archived', (SELECT COUNT(*) FROM transaction_archive),
        'archive_size_mb', ROUND(
            (SELECT SUM(data_length + index_length) / 1024 / 1024 
             FROM information_schema.tables 
             WHERE table_schema = DATABASE() 
             AND table_name = 'transaction_archive'), 2
        ),
        'main_table_size_mb', ROUND(
            (SELECT SUM(data_length + index_length) / 1024 / 1024 
             FROM information_schema.tables 
             WHERE table_schema = DATABASE() 
             AND table_name = 'transactions'), 2
        ),
        'oldest_transaction', (SELECT MIN(created_at) FROM transactions),
        'newest_archive', (SELECT MAX(archived_at) FROM transaction_archive),
        'archive_enabled', (SELECT config_value FROM archiving_config WHERE config_key = 'archive_enabled'),
        'archive_days_old', (SELECT config_value FROM archiving_config WHERE config_key = 'archive_days_old')
    ) INTO json_result;
    
    RETURN json_result;
END //

DELIMITER ;

