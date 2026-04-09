ALTER TABLE transactions
    ADD COLUMN explanation_reasons_json TEXT NULL AFTER triggered_rules;