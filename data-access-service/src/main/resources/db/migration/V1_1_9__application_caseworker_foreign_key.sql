ALTER TABLE public.application
ADD COLUMN caseworker_id UUID NULL
CONSTRAINT application_caseworkers_id_fkey REFERENCES caseworkers(id);