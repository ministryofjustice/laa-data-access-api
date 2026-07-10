package uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure;

import java.util.UUID;

/** Gateway interface for caseworker existence checks in the getAllApplications use case. */
public interface GetAllApplicationsCaseworkerGateway {

  /**
   * Returns {@code true} if a caseworker with the given ID exists.
   *
   * @param userId the caseworker ID to check
   * @return {@code true} if found; {@code false} otherwise
   */
  boolean caseworkerExists(UUID userId);
}
