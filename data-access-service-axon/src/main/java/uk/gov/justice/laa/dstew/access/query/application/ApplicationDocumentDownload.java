package uk.gov.justice.laa.dstew.access.query.application;

import org.springframework.core.io.Resource;

/** Streamable content and available metadata for an Application document. */
public record ApplicationDocumentDownload(String fileName, String mediaType, Resource resource) {}
