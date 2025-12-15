ALTER TABLE public.applications
ADD COLUMN apply_application_id UUID NOT NULL,
ADD COLUMN submitted_at TIMESTAMPTZ NOT NULL,
ADD COLUMN used_delegated_functions BOOLEAN NOT NULL,
ADD COLUMN category_of_law VARCHAR,
ADD COLUMN matter_types VARCHAR,
ADD COLUMN is_auto_granted BOOLEAN NOT NULL;