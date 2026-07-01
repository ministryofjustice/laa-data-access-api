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
   * Persists changes to an existing application. Implementations must load the managed entity
   * before applying changes to preserve the {@code @Version} field.
   *
   * @param caseworkerAssignment the projection carrying the updated caseworker ID
   */
  void save(AssignCaseworkerApplication caseworkerAssignment);
}
