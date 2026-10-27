ALTER TABLE work_list_item RENAME COLUMN submitted_at TO ready_at;

ALTER TABLE work_list_item ADD COLUMN submitted_at TIMESTAMPTZ;

ALTER TABLE work_list_item ADD COLUMN autogranted VARCHAR(32);
