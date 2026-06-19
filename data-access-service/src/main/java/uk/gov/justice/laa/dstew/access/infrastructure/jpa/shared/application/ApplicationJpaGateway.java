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
    ApplicationEntity entity = mapper.toEntity(domain);
    ApplicationEntity saved = applicationRepository.save(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public boolean existsByApplyApplicationId(UUID applyApplicationId) {
    return applicationRepository.existsByApplyApplicationId(applyApplicationId);
  }

  @Override
  public Optional<ApplicationDomain> findLeadByApplyApplicationId(UUID applyApplicationId) {
    ApplicationEntity entity = applicationRepository.findByApplyApplicationId(applyApplicationId);
    return Optional.ofNullable(entity).map(mapper::toDomain);
  }

  @Override
  public List<UUID> findMissingApplyApplicationIds(List<UUID> applyApplicationIds) {
    List<UUID> found =
        applicationRepository.findAllByApplyApplicationIdIn(applyApplicationIds).stream()
            .map(ApplicationEntity::getApplyApplicationId)
            .toList();
    return applyApplicationIds.stream().filter(id -> !found.contains(id)).toList();
  }
}
