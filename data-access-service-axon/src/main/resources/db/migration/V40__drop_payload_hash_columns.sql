-- The request fingerprints kept on events (requestFingerprint, decisionRequestFingerprint) are the
-- only fingerprints read. The payload hashes copied onto each data row were never read, and the
-- raw requests they were computed from are no longer kept.
ALTER TABLE application_data DROP COLUMN payload_hash;

ALTER TABLE prior_authority_data DROP COLUMN payload_hash;

ALTER TABLE prior_authority_draft DROP COLUMN payload_hash;
