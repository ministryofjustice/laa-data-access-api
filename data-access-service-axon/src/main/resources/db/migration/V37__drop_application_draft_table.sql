-- Draft content is now carried by the Application's events (ApplicationDraftStartedEvent and
-- ApplicationDocumentUploadedEvent), with its content in event_content so it can be erased.
DROP TABLE application_draft;
