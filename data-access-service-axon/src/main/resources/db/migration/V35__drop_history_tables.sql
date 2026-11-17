-- Application history is now read from the lifecycle stream and the events themselves.
DROP TABLE application_history;
DROP TABLE prior_authority_history;

DELETE FROM token_entry
WHERE processor_name IN ('application-history-projection', 'prior-authority-history-projection');
