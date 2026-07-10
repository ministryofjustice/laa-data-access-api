package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
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

  @Override
  public ApplicationDomain update(UUID id, String status, Map<String, Object> applicationContent) {
    ApplicationEntity entity =
        applicationRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with id: " + id));

    // Update the managed entity directly to preserve @Version semantics.
    if (status != null) {
      entity.setStatus(ApplicationStatus.valueOf(status));
    }
    entity.setApplicationContent(applicationContent);
    entity.setModifiedAt(Instant.now());

    ApplicationEntity saved = applicationRepository.save(entity);
    return mapper.toApplicationDomain(saved);
  }

  public boolean applicationExists(UUID applicationId) {
    return applicationRepository.findById(applicationId).isPresent();
  }
}
