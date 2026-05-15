ALTER TABLE executions
    ADD COLUMN name VARCHAR(200) NOT NULL DEFAULT 'Test execution';

ALTER TABLE executions
    ADD COLUMN total_duration_ms BIGINT;

ALTER TABLE executions
    ADD COLUMN summary TEXT;

ALTER TABLE execution_step_logs
    ADD COLUMN step_order INTEGER;

CREATE TABLE test_validation_results (
    id BIGSERIAL PRIMARY KEY,
    execution_step_id BIGINT NOT NULL REFERENCES execution_step_logs(id),
    assertion_name VARCHAR(200) NOT NULL,
    expected_value TEXT,
    actual_value TEXT,
    passed BOOLEAN NOT NULL,
    message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_execution_step_logs_status_duration
    ON execution_step_logs(status, duration_ms);

CREATE INDEX idx_execution_step_logs_created_at
    ON execution_step_logs(created_at);

CREATE INDEX idx_test_validation_results_step_id
    ON test_validation_results(execution_step_id);
