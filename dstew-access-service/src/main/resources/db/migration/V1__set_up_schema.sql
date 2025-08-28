-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Application Table
CREATE TABLE applications (
                              id UUID PRIMARY KEY,
                              provider_office_id VARCHAR(255) NULL,
                              status_code VARCHAR(100) NULL,

                              created_at TIMESTAMP,
                              created_by VARCHAR(255),
                              updated_at TIMESTAMP,
                              updated_by VARCHAR(255)
);

-- Draft Application Table
CREATE TABLE draft_applications (
                                    id UUID PRIMARY KEY,
                                    provider_id UUID NULL,
                                    client_id UUID NULL,
                                    additional_data JSONB NULL
);