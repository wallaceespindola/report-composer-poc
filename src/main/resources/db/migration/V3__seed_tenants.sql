-- Seed tenants (countries) and their report contracts (PRD §3.5).
-- Accounts and transactions are seeded at startup by DataSeeder (volume is configurable
-- via SEED_ACCOUNTS_PER_TENANT), guarded to run only when the account table is empty.

INSERT INTO tenant (tenant_id, country_code, locale, currency, enabled, config_json)
VALUES ('BE', 'BE', 'nl-BE', 'EUR', TRUE, '{"eligibleCriteria": "eligible = true"}');

INSERT INTO tenant (tenant_id, country_code, locale, currency, enabled, config_json)
VALUES ('FR', 'FR', 'fr-FR', 'EUR', TRUE, '{"eligibleCriteria": "eligible = true"}');

INSERT INTO tenant (tenant_id, country_code, locale, currency, enabled, config_json)
VALUES ('ES', 'ES', 'es-ES', 'EUR', TRUE, '{"eligibleCriteria": "eligible = true"}');

INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('BE', 'ACCOUNT_STATEMENT', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');
INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('BE', 'TAX_SUMMARY', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');

INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('FR', 'ACCOUNT_STATEMENT', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');
INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('FR', 'TAX_SUMMARY', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');

INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('ES', 'ACCOUNT_STATEMENT', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');
INSERT INTO tenant_report_contract (tenant_id, report_type, enabled, effective_from, params_json)
VALUES ('ES', 'TAX_SUMMARY', TRUE, DATE '2026-01-01', '{"outputFormat": "TXT"}');
