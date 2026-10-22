-- Projection-owned linked-group assignment tracking. Each table pair is written and reset only by its owning query projection; the command side never reads them.
CREATE TABLE work_list_assignment_group (
    group_id UUID PRIMARY KEY,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE TABLE work_list_assignment_application (
    application_id UUID PRIMARY KEY,
    group_id UUID,
    active BOOLEAN NOT NULL,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE INDEX idx_work_list_assignment_application_group_id ON work_list_assignment_application (group_id);

CREATE TABLE application_projection_assignment_group (
    group_id UUID PRIMARY KEY,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE TABLE application_projection_assignment_application (
    application_id UUID PRIMARY KEY,
    group_id UUID,
    active BOOLEAN NOT NULL,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE INDEX idx_application_projection_assignment_application_group_id ON application_projection_assignment_application (group_id);

CREATE TABLE application_list_index_assignment_group (
    group_id UUID PRIMARY KEY,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE TABLE application_list_index_assignment_application (
    application_id UUID PRIMARY KEY,
    group_id UUID,
    active BOOLEAN NOT NULL,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE INDEX idx_application_list_index_assignment_application_group_id ON application_list_index_assignment_application (group_id);

CREATE TABLE application_history_assignment_group (
    group_id UUID PRIMARY KEY,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE TABLE application_history_assignment_application (
    application_id UUID PRIMARY KEY,
    group_id UUID,
    active BOOLEAN NOT NULL,
    caseworker_id UUID,
    assignment_version BIGINT NOT NULL
);
CREATE INDEX idx_application_history_assignment_application_group_id ON application_history_assignment_application (group_id);
