ALTER TABLE api_inventories
    ADD COLUMN request_schema TEXT;

ALTER TABLE api_inventories
    ADD COLUMN response_schema TEXT;
