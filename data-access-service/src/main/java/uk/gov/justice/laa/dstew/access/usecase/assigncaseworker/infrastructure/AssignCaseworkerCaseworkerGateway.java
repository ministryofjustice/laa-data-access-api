package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure;

import java.util.UUID;

/** Gateway interface for caseworker existence checks in the assignCaseworker use case. */
public interface AssignCaseworkerCaseworkerGateway {

  /**
   * Returns {@code true} if a caseworker with the given ID exists in the repository.
   *
   * @param caseworkerId the caseworker ID to check
   * @return {@code true} if found, {@code false} otherwise
   */
  boolean exists(UUID caseworkerId);
}
