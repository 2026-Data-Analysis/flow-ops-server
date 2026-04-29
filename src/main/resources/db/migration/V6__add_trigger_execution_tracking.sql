ALTER TABLE trigger_rules
    ADD COLUMN last_triggered_at TIMESTAMP;

CREATE INDEX idx_trigger_rules_schedule_enabled
    ON trigger_rules(trigger_type, enabled, last_triggered_at);
