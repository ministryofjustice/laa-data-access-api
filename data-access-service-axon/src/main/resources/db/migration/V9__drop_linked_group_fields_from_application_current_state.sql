ALTER TABLE application_current_state
    DROP COLUMN IF EXISTS is_lead,
    DROP COLUMN IF EXISTS group_id;
