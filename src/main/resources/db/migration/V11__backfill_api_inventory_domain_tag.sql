UPDATE api_inventories
SET domain_tag = UPPER(REGEXP_REPLACE(REGEXP_REPLACE(TRIM(BOTH '/' FROM endpoint_path), '/.*$', ''), '[^A-Za-z0-9]+', '_', 'g'))
WHERE (domain_tag IS NULL OR TRIM(domain_tag) = '')
  AND endpoint_path IS NOT NULL
  AND TRIM(BOTH '/' FROM endpoint_path) <> ''
  AND REGEXP_REPLACE(TRIM(BOTH '/' FROM endpoint_path), '/.*$', '') NOT LIKE '{%';
