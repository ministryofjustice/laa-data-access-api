package uk.gov.justice.laa.dstew.access.service.sds;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire response containing an SDS signed download URL. */
public record SdsDownloadResult(@JsonProperty("fileURL") String fileUrl) {}
