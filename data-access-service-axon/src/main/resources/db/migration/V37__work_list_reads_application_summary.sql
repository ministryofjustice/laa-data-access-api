-- The work list takes application facts from the application summary at read time instead of
-- copying them from application_data, so the copied columns are dropped. Work-item rows need no
-- rebuild; their workflow columns are unchanged.
ALTER TABLE work_list_item
    DROP COLUMN IF EXISTS laa_reference,
    DROP COLUMN IF EXISTS used_delegated_functions,
    DROP COLUMN IF EXISTS category_of_law,
    DROP COLUMN IF EXISTS category_of_law_code,
    DROP COLUMN IF EXISTS matter_types,
    DROP COLUMN IF EXISTS matter_type_codes;
