package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when manual readiness would overwrite an existing automatic-grant outcome. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationAutoGrantOutcomeConflictException extends RuntimeException {

  public ApplicationAutoGrantOutcomeConflictException(UUID applicationId) {
    super("Application " + applicationId + " already has an incompatible auto-grant outcome");
  }
}
