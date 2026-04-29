ALTER TABLE environments
    ADD COLUMN repository_id BIGINT REFERENCES project_repositories(id);

ALTER TABLE environments
    ADD COLUMN branch_name VARCHAR(100);

ALTER TABLE environments
    ADD COLUMN default_test_level_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE trigger_rules
    ADD COLUMN trigger_config TEXT;

UPDATE trigger_rules
SET scope_type = 'BY_TAGS'
WHERE scope_type = 'TAG';

CREATE INDEX idx_environments_repository_branch
    ON environments(repository_id, branch_name);

CREATE INDEX idx_environments_app_branch
    ON environments(app_id, branch_name);
