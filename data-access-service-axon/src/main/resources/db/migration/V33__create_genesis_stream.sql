-- One pointer per event per genesis (root) application, so an application's history can be read
-- across every aggregate that appended to it. Pointers name the event by source and identifier
-- rather than by store position, so other sources can contribute later; there is no foreign key
-- for the same reason. Rebuilt by replaying the genesis-stream processor.
CREATE TABLE genesis_stream (
    id                BIGSERIAL     PRIMARY KEY,
    genesis_id        UUID          NOT NULL,
    source            VARCHAR(64)   NOT NULL,
    event_identifier  VARCHAR(255)  NOT NULL,
    event_name        VARCHAR(255)  NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT genesis_stream_event_uk UNIQUE (genesis_id, source, event_identifier)
);

CREATE INDEX genesis_stream_genesis_id_occurred_at_idx
    ON genesis_stream (genesis_id, occurred_at, id);
