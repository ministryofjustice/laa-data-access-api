package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;

/** Internal result of a Prior Authority document type update. */
public record UpdatePriorAuthorityDocumentTypeResult(UUID documentId, Instant updatedAt) {}
