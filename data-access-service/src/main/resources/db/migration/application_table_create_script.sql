CREATE TABLE IF NOT EXISTS application(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  status_id UUID NOT NULL,
  application_content JSONB NOT NULL,
  schema_version INTEGER,
  CONSTRAINT application_pkey PRIMARY KEY (ID),
  CONSTRAINT application_status_id_fkey FOREIGN KEY(status_id) REFERENCES status_code_lookup(ID)
)

-- INSERT INTO public.application(
-- 	id, status_id, application_content, schema_version)
-- 	VALUES ('e32a59fc-94ba-44ed-a74e-2296b8d56716', 'c54cf397-e574-4bb3-b6b8-80548f87499a', '{}', 1);