ALTER TABLE api_inventories
    ADD COLUMN branch_name VARCHAR(100);

CREATE INDEX idx_api_inventories_repository_branch
    ON api_inventories(repository_id, branch_name);
