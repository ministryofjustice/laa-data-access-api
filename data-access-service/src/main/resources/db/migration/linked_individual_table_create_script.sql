CREATE TABLE IF NOT EXISTS linked_individual(
  ID UUID NOT NULL,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
  application_id UUID NOT NULL,
  individual_id UUID NOT NULL,

  CONSTRAINT linked_individual_pkey PRIMARY KEY (ID),
  CONSTRAINT linked_individual_application_id_fkey FOREIGN KEY(application_id) REFERENCES application(ID),
  CONSTRAINT linked_individual_individual_id_fkey FOREIGN KEY (individual_id) REFERENCES individual(ID)
)

-- INSERT INTO public.linked_individual(
-- 	id, application_id, individual_id)
-- 	VALUES ('046dd449-e3cf-4ab6-b4c4-b60906cd3f37', 'e32a59fc-94ba-44ed-a74e-2296b8d56716', 'eec5674a-6375-49b7-96a4-27608657bdef');