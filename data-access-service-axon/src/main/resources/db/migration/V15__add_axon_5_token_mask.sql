-- Axon 5 represents a tracking segment by its id and mask. Axon 4 stored only the id.
-- Existing segment ids are contiguous from 0 through the processor's mask.
ALTER TABLE token_entry ADD COLUMN mask INTEGER;

UPDATE token_entry target
SET mask = processor_segments.max_segment
FROM (
    SELECT processor_name, MAX(segment) AS max_segment
    FROM token_entry
    GROUP BY processor_name
) processor_segments
WHERE target.processor_name = processor_segments.processor_name;

ALTER TABLE token_entry ALTER COLUMN mask SET NOT NULL;
ALTER TABLE token_entry ALTER COLUMN mask SET DEFAULT 0;

