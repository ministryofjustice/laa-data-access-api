package uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure;

import java.util.UUID;

/** Gateway interface for linked-application persistence in the createApplication use case. */
public interface LinkedApplicationGateway {

  /**
   * Creates the linked-application relationship between a lead and an associated application.
   *
   * @param leadApplicationId the ID of the lead application
   * @param associatedApplicationId the ID of the associated application
   */
  void link(UUID leadApplicationId, UUID associatedApplicationId);
}
