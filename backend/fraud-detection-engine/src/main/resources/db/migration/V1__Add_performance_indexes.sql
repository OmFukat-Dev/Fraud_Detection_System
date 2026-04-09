-- Add performance indexes for fraud detection system
-- Migration: V1__Add_performance_indexes.sql

-- Index for user-based queries
CREATE INDEX IF NOT EXISTS idx_transaction_user_id ON transactions(user_id);

-- Index for merchant-based queries  
CREATE INDEX IF NOT EXISTS idx_transaction_merchant_id ON transactions(merchant_id);

-- Index for fraud verdict filtering
CREATE INDEX IF NOT EXISTS idx_transaction_fraud_verdict ON transactions(fraud_verdict);

-- Index for time-based queries and pagination
CREATE INDEX IF NOT EXISTS idx_transaction_created_at ON transactions(created_at);

-- Composite index for common query patterns (user + time)
CREATE INDEX IF NOT EXISTS idx_transaction_user_created ON transactions(user_id, created_at);

-- Composite index for fraud analysis (verdict + time)
CREATE INDEX IF NOT EXISTS idx_transaction_verdict_created ON transactions(fraud_verdict, created_at);

-- Composite index for merchant fraud analysis
CREATE INDEX IF NOT EXISTS idx_transaction_merchant_verdict ON transactions(merchant_id, fraud_verdict);

-- Index for device-based queries
CREATE INDEX IF NOT EXISTS idx_transaction_device_id ON transactions(device_id);

-- Index for IP-based queries
CREATE INDEX IF NOT EXISTS idx_transaction_ip_address ON transactions(ip_address);

-- Composite index for geo-based queries
CREATE INDEX IF NOT EXISTS idx_transaction_geo ON transactions(latitude, longitude);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_transaction_status ON transactions(status);

