package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.UUID;

/** Audit representation of a prior-authority document deletion request. */
record DeletePriorAuthorityDocumentRequest(UUID documentId) {}
