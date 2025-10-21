CREATE TABLE IF NOT EXISTS status_code_lookup(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  code VARCHAR NOT NULL,
  description VARCHAR NOT NULL,
  CONSTRAINT status_code_lookup_pkey PRIMARY KEY (ID)
);

CREATE TABLE IF NOT EXISTS application(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  status_id UUID NOT NULL,
  application_content JSONB NOT NULL,
  schema_version INTEGER,
  CONSTRAINT application_pkey PRIMARY KEY (ID),
  CONSTRAINT application_status_id_fkey FOREIGN KEY(status_id) REFERENCES status_code_lookup(ID)
);

CREATE TABLE IF NOT EXISTS individual(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  first_name VARCHAR NOT NULL,
  last_name VARCHAR NOT NULL,
  individual_content JSONB NOT NULL,
  CONSTRAINT individual_pkey PRIMARY KEY (ID)
);

CREATE TABLE IF NOT EXISTS linked_individual(
  ID UUID NOT NULL,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  application_id UUID NOT NULL,
  individual_id UUID NOT NULL,

  CONSTRAINT linked_individual_pkey PRIMARY KEY (ID),
  CONSTRAINT linked_individual_application_id_fkey FOREIGN KEY(application_id) REFERENCES application(ID),
  CONSTRAINT linked_individual_individual_id_fkey FOREIGN KEY (individual_id) REFERENCES individual(ID)
);