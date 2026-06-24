package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for application read/write operations in the assignCaseworker use case. */
public interface AssignCaseworkerApplicationGateway {

  /**
   * Returns all applications whose IDs are in the supplied list.
   *
   * @param ids the application IDs to look up
   * @return found domain records (may be fewer than {@code ids} if some are missing)
   */
  List<ApplicationDomain> findAllByIds(List<UUID> ids);

  /**
   * Persists changes to an existing application. Implementations must load the managed entity
   * before applying changes to preserve the {@code @Version} field.
   *
   * @param domain the domain record carrying the updated caseworker ID
   */
  void save(ApplicationDomain domain);
}
