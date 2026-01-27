UPDATE public.applications SET status = 'APPLICATION_SUBMITTED' WHERE status = 'SUBMITTED';
UPDATE public.applications SET status = 'APPLICATION_IN_PROGRESS' WHERE status = 'IN_PROGRESS';