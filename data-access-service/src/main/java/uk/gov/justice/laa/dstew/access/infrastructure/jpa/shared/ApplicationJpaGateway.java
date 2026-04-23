package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntityV2;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepositoryV2;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** JPA implementation of ApplicationGateway using V2 entities. Wired via config (no @Component). */
public class ApplicationJpaGateway implements ApplicationGateway {

  private final ApplicationRepositoryV2 applicationRepository;
  private final ApplicationGatewayMapper mapper;

  public ApplicationJpaGateway(
      ApplicationRepositoryV2 applicationRepository, ApplicationGatewayMapper mapper) {
    this.applicationRepository = applicationRepository;
    this.mapper = mapper;
  }

  @Override
  public ApplicationDomain loadById(UUID id) {
    ApplicationEntityV2 entity =
        applicationRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with id: " + id));
    return mapper.toDomain(entity);
  }

  @Override
  public ApplicationDomain save(ApplicationDomain domain) {
    if (domain.id() == null) {
      // INSERT — build fresh entity; JPA cascades proceedings + decision
      return mapper.toDomain(applicationRepository.save(mapper.toNewEntity(domain)));
    }
    // UPDATE — load managed entity, apply in-place, save
    ApplicationEntityV2 entity =
        applicationRepository
            .findById(domain.id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("No application found with id: " + domain.id()));
    mapper.applyToEntity(domain, entity);
    return mapper.toDomain(applicationRepository.save(entity));
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
    ApplicationEntityV2 leadEntity =
        applicationRepository
            .findById(lead.id())
            .orElseThrow(
                () -> new ResourceNotFoundException("Lead application not found: " + lead.id()));
    ApplicationEntityV2 linkedEntity =
        applicationRepository
            .findById(linked.id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Linked application not found: " + linked.id()));
    leadEntity.addLinkedApplication(linkedEntity);
    return mapper.toDomain(applicationRepository.save(leadEntity));
  }
}
