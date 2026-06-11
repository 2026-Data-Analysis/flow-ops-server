ALTER TABLE project_repositories
    ADD COLUMN auto_sync_enabled BOOLEAN NOT NULL DEFAULT TRUE;
