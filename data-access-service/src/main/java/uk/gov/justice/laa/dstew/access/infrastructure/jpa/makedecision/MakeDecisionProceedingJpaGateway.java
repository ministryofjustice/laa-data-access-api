package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionProceedingGateway;

/** JPA implementation of {@link MakeDecisionProceedingGateway}. */
public class MakeDecisionProceedingJpaGateway implements MakeDecisionProceedingGateway {

  private final ProceedingRepository proceedingRepository;

  /**
   * Constructs the gateway.
   *
   * @param proceedingRepository the Spring Data repository
   */
  public MakeDecisionProceedingJpaGateway(ProceedingRepository proceedingRepository) {
    this.proceedingRepository = proceedingRepository;
  }

  @Override
  public Set<UUID> findExistingIds(List<UUID> ids) {
    return proceedingRepository.findAllById(ids).stream()
        .map(ProceedingEntity::getId)
        .collect(Collectors.toSet());
  }
}
