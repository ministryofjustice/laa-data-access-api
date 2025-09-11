-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Application Table
CREATE TABLE applications (
                              id UUID PRIMARY KEY,
                              provider_firm_id VARCHAR(255) NOT NULL,
                              provider_office_id VARCHAR(255) NOT NULL,
                              client_id UUID NOT NULL,
                              status_code VARCHAR(100),
                              statement_of_case VARCHAR(1000),
                              is_emergency_application BOOLEAN,

                              created_at TIMESTAMP NOT NULL,
                              created_by VARCHAR(255) NOT NULL,
                              updated_at TIMESTAMP NOT NULL,
                              updated_by VARCHAR(255) NOT NULL
);

-- ApplicationProceeding Table
CREATE TABLE applications_proceedings (
                                          id UUID PRIMARY KEY,
                                          application_id UUID NOT NULL,
                                          proceeding_code VARCHAR(100),
                                          level_of_service_code VARCHAR(100),

                                          created_at TIMESTAMP NOT NULL,
                                          created_by VARCHAR(255) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL,
                                          updated_by VARCHAR(255) NOT NULL,

                                          CONSTRAINT fk_application FOREIGN KEY (application_id)
                                              REFERENCES applications(id) ON DELETE CASCADE
);

-- Application History Table
CREATE TABLE application_history (
                                     id UUID PRIMARY KEY,
                                     application_id UUID NOT NULL,
                                     user_id VARCHAR(255) NOT NULL,
                                     resource_type_changed VARCHAR(100) NOT NULL,
                                     action VARCHAR(100) NOT NULL,
                                     timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     historic_snapshot JSONB NULL, -- was NOT NULL but initial data load failed
                                     application_snapshot JSONB NOT NULL,

                                     CONSTRAINT fk_application_history_application FOREIGN KEY (application_id)
                                         REFERENCES applications(id) ON DELETE CASCADE
);