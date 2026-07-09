package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.model.AssignCaseworkerApplication;

/** Gateway interface for application read/write operations in the assignCaseworker use case. */
public interface AssignCaseworkerApplicationGateway {

  /**
   * Returns the projections for the given application IDs.
   *
   * @param ids the application IDs to look up
   * @return the matching projections
   */
  List<AssignCaseworkerApplication> findAllByIds(List<UUID> ids);

  /**
   * Persists the caseworker assignment for all supplied applications. Implementations must load
   * each managed entity before applying changes to preserve the {@code @Version} field.
   *
   * @param applications the applications to update
   * @param caseworkerId the caseworker to assign
   */
  void saveAll(List<AssignCaseworkerApplication> applications, UUID caseworkerId);
}
