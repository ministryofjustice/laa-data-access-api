package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;

/** Document metadata and streamable content, independent of application type. */
public record EvidenceDocumentDownload(EvidenceDocument document, Resource resource) {}
