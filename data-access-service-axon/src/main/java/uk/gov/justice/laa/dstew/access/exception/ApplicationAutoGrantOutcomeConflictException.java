package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when manual readiness would overwrite an existing automatic-grant outcome. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationAutoGrantOutcomeConflictException extends RuntimeException
    implements ClientFacingException {

  public ApplicationAutoGrantOutcomeConflictException(UUID applicationId) {
    super("Application " + applicationId + " already has an incompatible auto-grant outcome");
  }
}
