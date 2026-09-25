package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Queries event-sourced document facts, including soft-deleted documents. */
public record FindPriorAuthorityDocumentDataQuery(UUID priorAuthorityId, UUID documentId) {}
