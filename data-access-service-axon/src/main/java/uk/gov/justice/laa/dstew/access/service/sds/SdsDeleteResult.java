package uk.gov.justice.laa.dstew.access.service.sds;

import java.util.List;

/** Per-file results of an SDS deletion request. */
public record SdsDeleteResult(List<DocumentDeletion> results) {
  /** SDS status for a requested file key. */
  public record DocumentDeletion(String documentId, Integer status) {}
}
