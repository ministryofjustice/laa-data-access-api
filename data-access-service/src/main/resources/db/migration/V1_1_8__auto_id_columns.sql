ALTER TABLE public.application 
ALTER COLUMN id SET DEFAULT uuid_generate_v4();

ALTER TABLE public.individual
ALTER COLUMN id SET DEFAULT uuid_generate_v4();