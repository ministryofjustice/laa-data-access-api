-- Drop the existing primary key constraint on ID
ALTER TABLE linked_individual DROP CONSTRAINT linked_individual_pkey;

-- Drop the ID column
ALTER TABLE linked_individual DROP COLUMN id;

-- Set a composite primary key on application_id + individual_id
ALTER TABLE linked_individual
    ADD PRIMARY KEY (application_id, individual_id);