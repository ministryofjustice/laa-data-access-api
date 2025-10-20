CREATE TABLE IF NOT EXISTS individual(
  ID UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  first_name VARCHAR NOT NULL,
  last_name VARCHAR NOT NULL,
  individual_content JSONB NOT NULL,
  CONSTRAINT individual_pkey PRIMARY KEY (ID)
)

-- INSERT INTO public.individual(
-- 	id, first_name, last_name, individual_content)
-- 	VALUES ('eec5674a-6375-49b7-96a4-27608657bdef', 'Jimi', 'Hendrix', '{
--     "address":{
--         "streetAddress" : "23 Brook Street, Mayfair, London",
--         "postalCode": "W1k 4HA"
--     }
-- }' )
