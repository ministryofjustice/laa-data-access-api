-- One pointer per event per genesis (root) application, so an application's history can be read
-- across every aggregate that appended to it. Pointers name the event by source and identifier
-- rather than by store position, so other sources can contribute later; there is no foreign key
-- for the same reason. Rebuilt by replaying the lifecycle-stream processor.
CREATE TABLE lifecycle_stream (
    id                BIGSERIAL     PRIMARY KEY,
    genesis_id        UUID          NOT NULL,
    source            VARCHAR(64)   NOT NULL,
    event_identifier  VARCHAR(255)  NOT NULL,
    event_name        VARCHAR(255)  NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT lifecycle_stream_event_uk UNIQUE (genesis_id, source, event_identifier)
);

CREATE INDEX lifecycle_stream_genesis_id_occurred_at_idx
    ON lifecycle_stream (genesis_id, occurred_at, id);
