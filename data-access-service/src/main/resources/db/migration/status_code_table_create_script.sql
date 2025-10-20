CREATE TABLE IF NOT EXISTS status_code_lookup(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  code VARCHAR NOT NULL,
  description VARCHAR NOT NULL,
  CONSTRAINT status_code_lookup_pkey PRIMARY KEY (ID)
)

-- INSERT INTO public.status_code_lookup(
-- 	id, code, description)
-- 	VALUES ('c54cf397-e574-4bb3-b6b8-80548f87499a', 'code4', 'someDescription');