package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** JPA implementation of ApplicationGateway. Wired via config (no @Component). */
public class ApplicationJpaGateway implements ApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final ApplicationGatewayMapper mapper;

  public ApplicationJpaGateway(
      ApplicationRepository applicationRepository, ApplicationGatewayMapper mapper) {
    this.applicationRepository = applicationRepository;
    this.mapper = mapper;
  }

  @Override
  public ApplicationDomain save(ApplicationDomain domain) {
    return mapper.toDomain(applicationRepository.save(mapper.toEntity(domain)));
  }

  @Override
  public boolean existsByApplyApplicationId(UUID applyApplicationId) {
    return applicationRepository.existsByApplyApplicationId(applyApplicationId);
  }

  @Override
  public Optional<ApplicationDomain> findByApplyApplicationId(UUID applyApplicationId) {
    return Optional.ofNullable(applicationRepository.findByApplyApplicationId(applyApplicationId))
        .map(mapper::toDomain);
  }

  @Override
  public ApplicationDomain addLinkedApplication(ApplicationDomain lead, ApplicationDomain linked) {
    ApplicationEntity leadEntity =
        applicationRepository
            .findById(lead.id())
            .orElseThrow(
                () -> new ResourceNotFoundException("Lead application not found: " + lead.id()));
    ApplicationEntity linkedEntity =
        applicationRepository
            .findById(linked.id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Linked application not found: " + linked.id()));
    leadEntity.addLinkedApplication(linkedEntity);
    return mapper.toDomain(applicationRepository.save(leadEntity));
  }

  @Override
  public ApplicationDomain findById(UUID id) {
    ApplicationEntity entity =
        applicationRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with id: " + id));
    return mapper.toDomain(entity);
  }

  @Override
  public ApplicationDomain updateAutoGranted(UUID applicationId, Boolean autoGranted) {
    ApplicationEntity entity =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application found with id: " + applicationId));
    entity.setIsAutoGranted(autoGranted);
    entity.setModifiedAt(Instant.now());
    return mapper.toDomain(applicationRepository.save(entity));
  }
}
