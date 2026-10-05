package uk.gov.justice.laa.dstew.access.document;

import org.springframework.core.io.Resource;

/** Document metadata and streamable content, independent of application type. */
public record DocumentDownload(DocumentDetails document, Resource resource) {}
