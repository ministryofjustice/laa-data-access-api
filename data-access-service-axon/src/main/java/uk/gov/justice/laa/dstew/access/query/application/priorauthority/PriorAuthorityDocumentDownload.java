package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;

/** Document metadata and streamable content for a Prior Authority download. */
public record PriorAuthorityDocumentDownload(PriorAuthorityDocument document, Resource resource) {}
