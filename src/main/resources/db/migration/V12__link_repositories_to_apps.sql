ALTER TABLE project_repositories
    ADD COLUMN app_id BIGINT REFERENCES apps(id);

CREATE INDEX idx_repositories_app_id ON project_repositories(app_id);
