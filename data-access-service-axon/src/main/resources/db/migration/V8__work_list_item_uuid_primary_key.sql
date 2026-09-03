ALTER TABLE work_list_item DROP CONSTRAINT work_list_item_pkey;
ALTER TABLE work_list_item ADD CONSTRAINT work_list_item_pkey PRIMARY KEY (item_id);

DROP INDEX IF EXISTS idx_work_list_item_open;
CREATE INDEX idx_work_list_item_open ON work_list_item (item_type, assignee_id, updated_at DESC);

