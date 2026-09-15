package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.UUID;

/** Request metadata retained for a prior-authority document upload. */
record UploadPriorAuthorityDocumentRequest(
    UUID documentId,
    String originalFilename,
    long fileSize,
    String fileType,
    String contentType,
    String sourceService,
    String checksum) {}
