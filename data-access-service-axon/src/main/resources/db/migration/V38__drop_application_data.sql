-- Application content now lives in event content, which erasure reaches; nothing writes or reads
-- application_data. The table, its append-only triggers and its retention-delete function go.
DROP FUNCTION IF EXISTS delete_application_data_for_retention(UUID);
DROP TABLE IF EXISTS application_data;
DROP FUNCTION IF EXISTS reject_application_data_mutation();
