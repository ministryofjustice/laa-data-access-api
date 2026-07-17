ALTER TABLE application_current_state
    ADD COLUMN is_lead  BOOLEAN,
    ADD COLUMN group_id UUID;
