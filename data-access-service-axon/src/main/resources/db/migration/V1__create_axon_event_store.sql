CREATE SEQUENCE domain_event_entry_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE domain_event_entry (
    global_index BIGINT NOT NULL DEFAULT nextval('domain_event_entry_seq'),
    event_identifier VARCHAR(255) NOT NULL,
    meta_data OID,
    payload OID NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    PRIMARY KEY (global_index),
    CONSTRAINT uk_domain_event_identifier UNIQUE (event_identifier),
    CONSTRAINT uk_domain_event_sequence UNIQUE (aggregate_identifier, sequence_number)
);

CREATE TABLE snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL,
    meta_data OID,
    payload OID NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number, type),
    CONSTRAINT uk_snapshot_event_identifier UNIQUE (event_identifier)
);

CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    owner VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    token OID,
    token_type VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);
