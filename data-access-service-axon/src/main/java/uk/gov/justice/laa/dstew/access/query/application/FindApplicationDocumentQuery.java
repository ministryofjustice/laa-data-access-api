package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Finds a live Application document with its filename hydrated, including for drafts. */
public record FindApplicationDocumentQuery(UUID applicationId, UUID documentId) {}
