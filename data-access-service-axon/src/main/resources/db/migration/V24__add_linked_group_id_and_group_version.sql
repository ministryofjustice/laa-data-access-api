ALTER TABLE application_current_state ADD COLUMN linked_group_id UUID;

CREATE INDEX idx_application_current_state_linked_group_id
    ON application_current_state (linked_group_id);

UPDATE application_current_state application
SET linked_group_id = linked_group.group_id
FROM linked_application_group_current_state linked_group
WHERE linked_group.member_ids @> jsonb_build_array(application.application_id::text);

ALTER TABLE linked_application_group_current_state
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Groups are established with two members and subsequently grow one member at a time.
UPDATE linked_application_group_current_state
SET version = jsonb_array_length(member_ids) - 2;
