package uk.gov.justice.laa.dstew.access.query.utils.security;

/** Immutable access restriction resolved while an authenticated request is active. */
public sealed interface ReadAccessScope
    permits UnrestrictedReadAccessScope, OfficeCodeReadAccessScope {

  /**
   * Returns whether a record owned by the given office is visible, matching the office-code {@link
   * ReadAccessPolicy} restrictions for reads that load a single entity in memory.
   */
  boolean permitsOfficeCode(String officeCode);
}
