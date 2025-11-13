ALTER TABLE application
DROP CONSTRAINT IF EXISTS application_status_id_fkey;

ALTER TABLE application
    RENAME COLUMN status_id TO status;

ALTER TABLE application
ALTER COLUMN status TYPE VARCHAR(20) USING status::VARCHAR;

ALTER TABLE application
    ALTER COLUMN status SET DEFAULT 'IN_PROGRESS';