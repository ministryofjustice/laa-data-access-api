package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for decision-update operations in the makeDecision use case. */
public interface MakeDecisionApplicationGateway {

  /**
   * Persists decision and merits-decision changes to an existing managed application (UPDATE path).
   * Implementations must reload the managed entity to avoid {@literal @}Version null.
   *
   * @param domain the updated application domain (id must be non-null)
   */
  void updateDecision(ApplicationDomain domain);
}
