CREATE TABLE work_list_group_member (
    application_id UUID PRIMARY KEY,
    group_id UUID NOT NULL
);

CREATE INDEX idx_work_list_group_member_group_id ON work_list_group_member (group_id);

INSERT INTO work_list_group_member (application_id, group_id)
SELECT application_id, linked_group_id
FROM application_current_state
WHERE linked_group_id IS NOT NULL;

UPDATE work_list_item item
SET assignment_boundary_type = 'LINKED_GROUP',
    assignment_boundary_id = member.group_id,
    group_id = member.group_id
FROM work_list_group_member member
WHERE item.item_type = 'APPLICATION'
  AND item.item_id = member.application_id;
