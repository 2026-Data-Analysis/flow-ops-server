ALTER TABLE execution_step_logs
    DROP CONSTRAINT IF EXISTS execution_step_logs_scenario_step_id_fkey;

ALTER TABLE execution_step_logs
    ADD CONSTRAINT execution_step_logs_scenario_step_id_fkey
        FOREIGN KEY (scenario_step_id)
        REFERENCES scenario_steps(id)
        ON DELETE SET NULL;
