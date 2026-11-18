package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Thrown when a prior-authority submission ID is reused on create. */
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityCreationConflictException extends RuntimeException
    implements ClientFacingException {

  private final UUID priorAuthorityId;

  /** Creates a conflict exception for a submission ID that already exists. */
  public PriorAuthorityCreationConflictException(UUID priorAuthorityId) {
    super("Prior authority already exists for submission: " + priorAuthorityId);
    this.priorAuthorityId = priorAuthorityId;
  }

  public UUID getPriorAuthorityId() {
    return priorAuthorityId;
  }
}
