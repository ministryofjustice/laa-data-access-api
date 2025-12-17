ALTER TABLE public.applications
ADD COLUMN apply_application_id UUID NULL,
ADD COLUMN submitted_at TIMESTAMPTZ NULL,
ADD COLUMN used_delegated_functions BOOLEAN NULL,
ADD COLUMN category_of_law VARCHAR NULL,
ADD COLUMN matter_types VARCHAR NULL,
ADD COLUMN is_auto_granted BOOLEAN NULL;