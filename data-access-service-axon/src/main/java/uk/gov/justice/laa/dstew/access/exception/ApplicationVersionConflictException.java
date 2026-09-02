package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when a command was based on a stale Application revision. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationVersionConflictException extends RuntimeException {

  public ApplicationVersionConflictException(UUID applicationId, long version) {
    super("Application with id " + applicationId + " and version " + version + " not found");
  }
}
