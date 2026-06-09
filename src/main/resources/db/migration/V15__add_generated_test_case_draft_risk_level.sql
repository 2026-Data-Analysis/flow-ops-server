ALTER TABLE generated_test_case_drafts
    ADD COLUMN risk_level VARCHAR(20);

UPDATE generated_test_case_drafts
SET risk_level = 'REGRESSION'
WHERE risk_level IS NULL;
