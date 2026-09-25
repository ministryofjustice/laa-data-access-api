package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Queries whether a document is still active in the prior-authority projection. */
public record PriorAuthorityDocumentPresentQuery(UUID priorAuthorityId, UUID documentId) {}
