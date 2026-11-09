-- Prior authority content now lives in event content, which erasure reaches; nothing writes or
-- reads prior_authority_data. The table and its append-only trigger function go, along with the
-- data version the current-state row kept.
DROP TABLE IF EXISTS prior_authority_data;
DROP FUNCTION IF EXISTS reject_prior_authority_data_mutation();
ALTER TABLE prior_authority_current_state DROP COLUMN IF EXISTS data_version;
