package uk.gov.justice.laa.dstew.access.service.sds;

/** Wire response from an SDS file replacement. */
public record SdsUpdateResult(String success, String checksum) {}
