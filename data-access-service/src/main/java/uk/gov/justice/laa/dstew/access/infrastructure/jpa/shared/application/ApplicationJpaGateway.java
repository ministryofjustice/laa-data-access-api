package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** JPA implementation of {@link ApplicationGateway}. */
public class ApplicationJpaGateway implements ApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final ApplicationGatewayMapper mapper;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the Spring Data repository
   * @param mapper the gateway mapper
   */
  public ApplicationJpaGateway(
      ApplicationRepository applicationRepository, ApplicationGatewayMapper mapper) {
    this.applicationRepository = applicationRepository;
    this.mapper = mapper;
  }

  @Override
  public ApplicationDomain save(ApplicationDomain domain) {
    ApplicationEntity entity = mapper.toApplicationEntity(domain);
    ApplicationEntity saved = applicationRepository.save(entity);
    return mapper.toApplicationDomain(saved);
  }

  @Override
  public boolean existsByApplyApplicationId(UUID applyApplicationId) {
    return applicationRepository.existsByApplyApplicationId(applyApplicationId);
  }

  @Override
  public Optional<ApplicationDomain> findByLeadApplyApplicationId(UUID applyApplicationId) {
    ApplicationEntity entity = applicationRepository.findByApplyApplicationId(applyApplicationId);
    return Optional.ofNullable(entity).map(mapper::toApplicationDomain);
  }

  @Override
  public Optional<ApplicationDomain> findByApplicationId(UUID applicationId) {
    return applicationRepository.findById(applicationId).map(mapper::toApplicationDomain);
  }

  @Override
  public List<UUID> findMissingApplyApplicationIds(List<UUID> applyApplicationIds) {
    List<UUID> found =
        applicationRepository.findAllByApplyApplicationIdIn(applyApplicationIds).stream()
            .map(ApplicationEntity::getApplyApplicationId)
            .toList();
    return applyApplicationIds.stream().filter(id -> !found.contains(id)).toList();
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
    mapper.applyDecisionToEntity(entity, domain);
    applicationRepository.save(entity);
  }
}
