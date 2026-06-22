package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.LinkedApplicationGateway;

/** JPA implementation of {@link LinkedApplicationGateway}. */
public class LinkedApplicationJpaGateway implements LinkedApplicationGateway {

  private final LinkedApplicationRepository linkedApplicationRepository;

  /**
   * Constructs the gateway.
   *
   * @param linkedApplicationRepository the Spring Data linked-application repository
   */
  public LinkedApplicationJpaGateway(LinkedApplicationRepository linkedApplicationRepository) {
    this.linkedApplicationRepository = linkedApplicationRepository;
  }

  @Override
  public void link(UUID leadApplicationId, UUID associatedApplicationId) {
    linkedApplicationRepository.save(
        LinkedApplicationEntity.builder()
            .leadApplicationId(leadApplicationId)
            .associatedApplicationId(associatedApplicationId)
            .build());
  }
}
