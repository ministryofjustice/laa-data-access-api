package uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for application read/write in the unassignCaseworker use case. */
public interface UnassignCaseworkerApplicationGateway {

  /**
   * Returns the application domain for the given ID, or empty if not found.
   *
   * @param id the application UUID
   * @return an {@link Optional} containing the domain record, or empty if not found
   */
  Optional<ApplicationDomain> findApplicationById(UUID id);

  /**
   * Persists the updated application domain. Implementations must load the managed entity before
   * applying changes to preserve the {@code @Version} field.
   *
   * @param applicationDomain the updated domain record
   */
  void saveApplication(ApplicationDomain applicationDomain);
}
