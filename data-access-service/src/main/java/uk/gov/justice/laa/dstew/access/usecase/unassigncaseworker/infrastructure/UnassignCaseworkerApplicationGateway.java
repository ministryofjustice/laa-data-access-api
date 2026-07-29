package uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.infrastructure;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for application read/write in the unassignCaseworker use case. */
public interface UnassignCaseworkerApplicationGateway {

  /**
   * Persists the updated application domain. Implementations must load the managed entity before
   * applying changes to preserve the {@code @Version} field.
   *
   * @param applicationDomain the updated domain record
   */
  void saveApplication(ApplicationDomain applicationDomain);
}
