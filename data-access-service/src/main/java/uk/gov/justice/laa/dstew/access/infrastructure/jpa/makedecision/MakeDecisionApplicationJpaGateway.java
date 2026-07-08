package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionApplicationGateway;

/** JPA implementation of {@link MakeDecisionApplicationGateway}. */
public class MakeDecisionApplicationJpaGateway implements MakeDecisionApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final MakeDecisionGatewayMapper makeDecisionGatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the Spring Data repository
   * @param makeDecisionGatewayMapper the makeDecision-specific mapper for in-place entity mutation
   */
  public MakeDecisionApplicationJpaGateway(
      ApplicationRepository applicationRepository,
      MakeDecisionGatewayMapper makeDecisionGatewayMapper) {
    this.applicationRepository = applicationRepository;
    this.makeDecisionGatewayMapper = makeDecisionGatewayMapper;
  }

  /**
   * Re-loads the managed entity by ID and applies decision changes in-place via the mapper. This
   * avoids the @Version null problem that would occur if a new detached entity were saved.
   */
  @Override
  public void updateDecision(ApplicationDomain domain) {
    ApplicationEntity entity =
        applicationRepository
            .findById(domain.id())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Application not found during update: " + domain.id()));
    makeDecisionGatewayMapper.applyDecisionToEntity(entity, domain);
    applicationRepository.save(entity);
  }
}
