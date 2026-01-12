package uk.gov.justice.laa.dstew.access.utils.records;

import lombok.Builder;

/**
   * Proceeding JSON Object for testing
   *
   * @param id
   * @param leadProceeding        is this the lead proceeding
   * @param categoryOfLaw         categoryOfLaw as string
   * @param matterType            matterType as string
   * @param useDelegatedFunctions useDelegatedFunctions flag
   */
  @Builder
  public record ProceedingJsonObject(
      String id,
      boolean leadProceeding,
      String categoryOfLaw,
      String matterType,
      boolean useDelegatedFunctions) {
  }