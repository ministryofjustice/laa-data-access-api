-- Application history is read from the lifecycle stream and the events themselves. Deploy only
-- once every running pod is on the release that stopped reading and projecting these tables.
DROP TABLE application_history;
DROP TABLE prior_authority_history;

DELETE FROM token_entry
WHERE processor_name IN ('application-history-projection', 'prior-authority-history-projection');
