package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Finds an active Prior Authority document, hydrating its filename when available. */
public record FindPriorAuthorityDocumentQuery(UUID priorAuthorityId, UUID documentId) {}
