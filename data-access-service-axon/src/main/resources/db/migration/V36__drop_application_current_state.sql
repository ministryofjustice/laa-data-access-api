-- The application summary now holds every application fact the list, the stalled-assessment
-- scan and linked-group lookups need; single-application reads load the aggregate. The
-- current-state projection and its processor are removed.
DROP TABLE IF EXISTS application_current_state;

DELETE FROM token_entry WHERE processor_name = 'application-projection';
