package uk.gov.justice.laa.dstew.access.service.sds;

/** Wire response from an SDS file upload. */
public record SdsUploadResult(String detail, String success, String checksum) {}
