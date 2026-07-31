package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Retrieves all notes for an Application by its internal identifier. */
public record FindNotesForApplicationQuery(UUID applicationId) {}
