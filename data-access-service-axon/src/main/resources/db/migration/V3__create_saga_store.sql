CREATE SEQUENCE association_value_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255),
    revision VARCHAR(255),
    serialized_saga OID,
    PRIMARY KEY (saga_id)
);

CREATE TABLE association_value_entry (
    id BIGINT NOT NULL DEFAULT nextval('association_value_entry_seq'),
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE INDEX idx_association_value_saga
    ON association_value_entry (saga_id, saga_type);

CREATE INDEX idx_association_value_lookup
    ON association_value_entry (saga_type, association_key, association_value);
