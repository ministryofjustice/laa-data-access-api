CREATE TABLE generation_jobs (
    id              VARCHAR(36) PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    target_count    INTEGER NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    decided_count   INTEGER NOT NULL DEFAULT 0,
    error_count     INTEGER NOT NULL DEFAULT 0,
    cleanup_requested BOOLEAN NOT NULL DEFAULT FALSE,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,
    error_message   TEXT,
    throughput      DOUBLE PRECISION,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
