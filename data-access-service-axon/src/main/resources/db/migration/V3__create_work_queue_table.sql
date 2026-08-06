CREATE TABLE work_queue_current_state (
    item_id     UUID         PRIMARY KEY,
    item_type   VARCHAR(50)  NOT NULL,
    assigned_to UUID,
    submitted_at TIMESTAMPTZ,
    laa_reference VARCHAR(255)
);

CREATE INDEX work_queue_assigned_to_idx ON work_queue_current_state (assigned_to);
CREATE INDEX work_queue_submitted_at_idx ON work_queue_current_state (submitted_at);
