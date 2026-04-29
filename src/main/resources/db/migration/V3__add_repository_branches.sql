CREATE TABLE repository_branches (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES project_repositories(id) ON DELETE CASCADE,
    branch_name VARCHAR(100) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    default_branch BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO repository_branches (
    repository_id,
    branch_name,
    selected,
    default_branch,
    created_at,
    updated_at
)
SELECT
    id,
    default_branch,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM project_repositories
WHERE default_branch IS NOT NULL;

CREATE INDEX idx_repository_branches_repository_id
    ON repository_branches(repository_id);

CREATE UNIQUE INDEX uk_repository_branches_repository_name
    ON repository_branches(repository_id, branch_name);

ALTER TABLE project_repositories
    DROP COLUMN IF EXISTS owner_name;
