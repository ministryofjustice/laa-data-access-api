-- Prior Authority draft content is carried by the Prior Authority's events
-- (PriorAuthorityDraftStarted, PriorAuthorityDraftUpdated and PriorAuthorityDocumentUploaded),
-- with its content in event_content so it can be erased. Reads fold it from the aggregate.
DROP TABLE prior_authority_draft;
