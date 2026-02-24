CREATE TABLE IF NOT EXISTS poc_linked_app(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lead_application_id UUID,
    reference VARCHAR NOT NULL)
