package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ApplicationGateway;

/** JPA implementation of ApplicationGateway. Wired via CreateApplicationConfig (no @Component). */
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
    ApplicationEntity leadEntity = mapper.toEntity(lead);
    leadEntity.addLinkedApplication(mapper.toEntity(linked));
    return mapper.toDomain(applicationRepository.save(leadEntity));
  }
}
