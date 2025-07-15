-- Application Table
CREATE TABLE draft_applications (
                              id UUID PRIMARY KEY,
                              provider_id UUID NULL,
                              client_id UUID NULL,
                              additional_data JSONB NULL
);
