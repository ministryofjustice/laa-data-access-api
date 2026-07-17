-- Migrate Axon @Lob columns from OID (PostgreSQL Large Object Storage) to BYTEA.
--
-- OID columns are not automatically vacuumed when rows are updated or deleted, causing
-- large-object table bloat. BYTEA uses transparent TOAST instead, which is self-managing.
-- See: https://docs.axoniq.io/axon-framework-reference/4.11/tuning/rdbms-tuning/

-- domain_event_entry.payload (NOT NULL)
ALTER TABLE domain_event_entry ADD COLUMN payload_bytea BYTEA;
UPDATE domain_event_entry SET payload_bytea = lo_get(payload);
ALTER TABLE domain_event_entry DROP COLUMN payload;
ALTER TABLE domain_event_entry RENAME COLUMN payload_bytea TO payload;
ALTER TABLE domain_event_entry ALTER COLUMN payload SET NOT NULL;

-- domain_event_entry.meta_data (nullable)
ALTER TABLE domain_event_entry ADD COLUMN meta_data_bytea BYTEA;
UPDATE domain_event_entry SET meta_data_bytea = lo_get(meta_data) WHERE meta_data IS NOT NULL;
ALTER TABLE domain_event_entry DROP COLUMN meta_data;
ALTER TABLE domain_event_entry RENAME COLUMN meta_data_bytea TO meta_data;

-- snapshot_event_entry.payload (NOT NULL)
ALTER TABLE snapshot_event_entry ADD COLUMN payload_bytea BYTEA;
UPDATE snapshot_event_entry SET payload_bytea = lo_get(payload);
ALTER TABLE snapshot_event_entry DROP COLUMN payload;
ALTER TABLE snapshot_event_entry RENAME COLUMN payload_bytea TO payload;
ALTER TABLE snapshot_event_entry ALTER COLUMN payload SET NOT NULL;

-- snapshot_event_entry.meta_data (nullable)
ALTER TABLE snapshot_event_entry ADD COLUMN meta_data_bytea BYTEA;
UPDATE snapshot_event_entry
SET meta_data_bytea = lo_get(meta_data)
WHERE meta_data IS NOT NULL;
ALTER TABLE snapshot_event_entry DROP COLUMN meta_data;
ALTER TABLE snapshot_event_entry RENAME COLUMN meta_data_bytea TO meta_data;

-- token_entry.token (nullable)
ALTER TABLE token_entry ADD COLUMN token_bytea BYTEA;
UPDATE token_entry SET token_bytea = lo_get(token) WHERE token IS NOT NULL;
ALTER TABLE token_entry DROP COLUMN token;
ALTER TABLE token_entry RENAME COLUMN token_bytea TO token;
