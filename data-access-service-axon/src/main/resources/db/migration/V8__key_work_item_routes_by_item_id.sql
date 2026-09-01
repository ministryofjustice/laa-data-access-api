ALTER TABLE work_item_route
    DROP CONSTRAINT work_item_route_pkey;

ALTER TABLE work_item_route
    ADD CONSTRAINT work_item_route_pkey PRIMARY KEY (work_item_id);

