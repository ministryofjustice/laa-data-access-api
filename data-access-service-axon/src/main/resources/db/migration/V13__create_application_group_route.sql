CREATE TABLE application_group_route (
    application_id UUID PRIMARY KEY,
    route_kind VARCHAR(32) NOT NULL,
    group_id UUID,
    office_code TEXT NOT NULL,
    route_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_application_group_route_kind
        CHECK (route_kind IN ('STANDALONE', 'LINKED_GROUP')),
    CONSTRAINT chk_application_group_route_membership
        CHECK (
            (route_kind = 'STANDALONE' AND group_id IS NULL)
            OR
            (route_kind = 'LINKED_GROUP' AND group_id IS NOT NULL)
        )
);

CREATE INDEX idx_application_group_route_group_id
    ON application_group_route (group_id);
