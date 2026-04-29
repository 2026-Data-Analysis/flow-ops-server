CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL UNIQUE,
    description VARCHAR(2000),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_repositories (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    provider VARCHAR(30) NOT NULL,
    repository_name VARCHAR(150) NOT NULL,
    full_name VARCHAR(255) NOT NULL UNIQUE,
    repository_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(100) NOT NULL,
    external_repository_id VARCHAR(100),
    connection_status VARCHAR(30) NOT NULL,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_inventories (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    repository_id BIGINT REFERENCES project_repositories(id),
    method VARCHAR(10) NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    operation_id VARCHAR(150),
    summary VARCHAR(500),
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    spec_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE environment_configs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    secret_reference VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE apps (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    repo_url VARCHAR(1000),
    spec_source VARCHAR(500),
    default_branch VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_endpoints (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    domain_tag VARCHAR(100),
    controller_name VARCHAR(150),
    request_schema TEXT,
    response_schema TEXT,
    is_deprecated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE environments (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    name VARCHAR(30) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    auth_config TEXT,
    headers TEXT,
    default_test_level VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trigger_rules (
    id BIGSERIAL PRIMARY KEY,
    environment_id BIGINT NOT NULL REFERENCES environments(id),
    trigger_type VARCHAR(20) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_value VARCHAR(2000),
    execution_mode VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_cases (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    api_id BIGINT NOT NULL REFERENCES api_endpoints(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    type VARCHAR(30) NOT NULL,
    test_level VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    user_role VARCHAR(100),
    state_condition VARCHAR(2000),
    data_variant VARCHAR(1000),
    request_spec TEXT,
    expected_spec TEXT,
    assertion_spec TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_case_versions (
    id BIGSERIAL PRIMARY KEY,
    test_case_id BIGINT NOT NULL REFERENCES test_cases(id),
    version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_generations (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    environment_id BIGINT REFERENCES environments(id),
    status VARCHAR(20) NOT NULL,
    requested_by VARCHAR(120) NOT NULL,
    context_summary VARCHAR(4000),
    current_coverage NUMERIC(5,2),
    predicted_coverage NUMERIC(5,2),
    existing_count INTEGER,
    new_count INTEGER,
    duplicate_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE test_generation_api_selections (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES test_generations(id),
    api_id BIGINT NOT NULL REFERENCES api_endpoints(id)
);

CREATE TABLE generated_test_case_drafts (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES test_generations(id),
    api_id BIGINT NOT NULL REFERENCES api_endpoints(id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    draft_type VARCHAR(40),
    user_role VARCHAR(100),
    state_condition VARCHAR(2000),
    data_variant VARCHAR(1000),
    request_spec TEXT,
    expected_spec TEXT,
    assertion_spec TEXT,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    selected_for_save BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE scenarios (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    type VARCHAR(30) NOT NULL,
    recommendation_reason VARCHAR(2000),
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE scenario_steps (
    id BIGSERIAL PRIMARY KEY,
    scenario_id BIGINT NOT NULL REFERENCES scenarios(id),
    step_order INTEGER NOT NULL,
    api_id BIGINT NOT NULL REFERENCES api_endpoints(id),
    label VARCHAR(200) NOT NULL,
    request_config TEXT,
    extract_rules TEXT,
    validation_rules TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE executions (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    environment_id BIGINT REFERENCES environments(id),
    execution_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,
    execution_mode VARCHAR(20) NOT NULL,
    test_level VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    passed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    avg_duration_ms BIGINT,
    created_by VARCHAR(120) NOT NULL,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE execution_step_logs (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES executions(id),
    test_case_id BIGINT REFERENCES test_cases(id),
    scenario_step_id BIGINT REFERENCES scenario_steps(id),
    step_name VARCHAR(200) NOT NULL,
    method VARCHAR(10),
    path VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    request_body TEXT,
    response_body TEXT,
    response_code INTEGER,
    duration_ms BIGINT,
    error_message VARCHAR(4000),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    execution_id BIGINT REFERENCES executions(id),
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    report_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_suggestions (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    suggestion_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_text TEXT,
    output_text TEXT,
    model_name VARCHAR(100),
    source_reference VARCHAR(255),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repositories_project_id ON project_repositories(project_id);
CREATE INDEX idx_api_inventories_project_id ON api_inventories(project_id);
CREATE INDEX idx_apps_name ON apps(name);
CREATE INDEX idx_api_endpoints_app_id ON api_endpoints(app_id);
CREATE INDEX idx_environments_app_id ON environments(app_id);
CREATE INDEX idx_test_cases_api_id ON test_cases(api_id);
CREATE INDEX idx_test_generations_app_id ON test_generations(app_id);
CREATE INDEX idx_scenarios_app_id ON scenarios(app_id);
CREATE INDEX idx_executions_app_id ON executions(app_id);
CREATE INDEX idx_execution_step_logs_execution_id ON execution_step_logs(execution_id);
CREATE INDEX idx_ai_suggestions_project_id ON ai_suggestions(project_id);
