ALTER TABLE public.applications
ADD COLUMN version BIGINT NULL;

UPDATE public.applications
SET version = 0
WHERE version IS NULL;
