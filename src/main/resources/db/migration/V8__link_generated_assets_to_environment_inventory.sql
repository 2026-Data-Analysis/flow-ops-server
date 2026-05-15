ALTER TABLE test_cases
    ADD COLUMN api_inventory_id BIGINT REFERENCES api_inventories(id);

ALTER TABLE test_generation_api_selections
    ADD COLUMN api_inventory_id BIGINT REFERENCES api_inventories(id);

ALTER TABLE generated_test_case_drafts
    ADD COLUMN api_inventory_id BIGINT REFERENCES api_inventories(id);

ALTER TABLE scenarios
    ADD COLUMN environment_id BIGINT REFERENCES environments(id);

ALTER TABLE scenario_steps
    ADD COLUMN api_inventory_id BIGINT REFERENCES api_inventories(id);

CREATE INDEX idx_test_cases_api_inventory_id
    ON test_cases(api_inventory_id);

CREATE INDEX idx_generation_selections_api_inventory_id
    ON test_generation_api_selections(api_inventory_id);

CREATE INDEX idx_generated_drafts_api_inventory_id
    ON generated_test_case_drafts(api_inventory_id);

CREATE INDEX idx_scenarios_environment_id
    ON scenarios(environment_id);

CREATE INDEX idx_scenario_steps_api_inventory_id
    ON scenario_steps(api_inventory_id);
