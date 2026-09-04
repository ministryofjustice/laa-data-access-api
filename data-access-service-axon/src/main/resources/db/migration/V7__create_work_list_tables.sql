CREATE TABLE work_item_route (
    work_item_type VARCHAR(32) NOT NULL,
    work_item_id UUID NOT NULL,
    route_kind VARCHAR(32) NOT NULL,
    group_id UUID,
    membership_version BIGINT NOT NULL DEFAULT 0,
    route_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (work_item_id),
    CONSTRAINT chk_work_item_route_kind CHECK (
        route_kind IN ('STANDALONE', 'PENDING_LINKED_GROUP', 'LINKED_GROUP', 'TRANSITIONING')
    )
);

CREATE INDEX idx_work_item_route_group_id ON work_item_route (group_id);

CREATE TABLE work_list_item (
    item_type VARCHAR(32) NOT NULL,
    item_id UUID NOT NULL,
    application_id UUID NOT NULL,
    parent_application_id UUID,
    laa_reference VARCHAR(255),
    submitted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    assignee_id UUID,
    assignment_boundary_type VARCHAR(32) NOT NULL,
    assignment_boundary_id UUID NOT NULL,
    group_id UUID,
    assignment_version BIGINT NOT NULL DEFAULT 0,
    item_version BIGINT NOT NULL,
    projection_position BIGINT NOT NULL,
    used_delegated_functions BOOLEAN,
    category_of_law VARCHAR(255),
    matter_types JSONB,
    application_status VARCHAR(64),
    PRIMARY KEY (item_id),
    CONSTRAINT chk_work_list_item_type CHECK (item_type IN ('APPLICATION', 'PRIOR_AUTHORITY')),
    CONSTRAINT chk_work_list_boundary CHECK (assignment_boundary_type IN ('DIRECT', 'LINKED_GROUP'))
);

CREATE INDEX idx_work_list_item_open ON work_list_item (item_type, assignee_id, updated_at DESC);
CREATE INDEX idx_work_list_item_application_id ON work_list_item (application_id);
CREATE INDEX idx_work_list_item_group_id ON work_list_item (group_id);

