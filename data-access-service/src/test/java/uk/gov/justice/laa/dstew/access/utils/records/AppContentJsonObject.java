package uk.gov.justice.laa.dstew.access.utils.records;

import java.util.List;
import lombok.Builder;

/**
 * Application Content JSON Object for testing
 *
 * @param proceedings
 * @param id
 * @param submittedAt
 */
  @Builder
  public record AppContentJsonObject(
      List<ProceedingJsonObject> proceedings,
      String id,
      String submittedAt) {
  }