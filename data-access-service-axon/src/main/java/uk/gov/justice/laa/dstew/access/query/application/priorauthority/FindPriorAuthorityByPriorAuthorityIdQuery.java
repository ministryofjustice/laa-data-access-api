package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

/** Query returning the current-state projection for a single prior-authority submission. */
public record FindPriorAuthorityByPriorAuthorityIdQuery(
    UUID priorAuthorityId, ReadAccessScope accessScope) {

  /** Creates an unrestricted query for non-request callers. */
  public FindPriorAuthorityByPriorAuthorityIdQuery(UUID priorAuthorityId) {
    this(priorAuthorityId, new UnrestrictedReadAccessScope());
  }
}
