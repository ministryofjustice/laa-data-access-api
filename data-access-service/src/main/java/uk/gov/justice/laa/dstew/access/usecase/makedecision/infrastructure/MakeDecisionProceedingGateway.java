package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gateway interface for proceeding validation in the makeDecision use case. */
public interface MakeDecisionProceedingGateway {

  /**
   * Returns the subset of the supplied IDs that exist in the database. Used to distinguish "not
   * found in DB" from "not linked to application".
   *
   * @param ids the list of proceeding IDs to check
   * @return a set containing only the IDs that exist in the DB
   */
  Set<UUID> findExistingIds(List<UUID> ids);
}
