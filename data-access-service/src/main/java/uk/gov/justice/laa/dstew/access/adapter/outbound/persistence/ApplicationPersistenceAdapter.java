package uk.gov.justice.laa.dstew.access.adapter.outbound.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.ApplicationPersistencePort;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

/**
 * Adapter that bridges the domain {@link ApplicationPersistencePort} to the Spring Data JPA {@link
 * ApplicationRepository}.
 */
@Component
@RequiredArgsConstructor
public class ApplicationPersistenceAdapter implements ApplicationPersistencePort {

  private final ApplicationRepository applicationRepository;
  private final DomainEntityMapper domainEntityMapper;

  @Override
  public Application save(Application application) {
    ApplicationEntity entity = domainEntityMapper.toEntity(application);
    ApplicationEntity saved = applicationRepository.save(entity);
    return domainEntityMapper.toDomain(saved);
  }

  @Override
  public boolean existsByApplyApplicationId(UUID applyApplicationId) {
    return applicationRepository.existsByApplyApplicationId(applyApplicationId);
  }

  @Override
  public Optional<Application> findByApplyApplicationId(UUID applyApplicationId) {
    ApplicationEntity entity = applicationRepository.findByApplyApplicationId(applyApplicationId);
    return Optional.ofNullable(domainEntityMapper.toDomain(entity));
  }

  @Override
  public List<Application> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds) {
    return applicationRepository.findAllByApplyApplicationIdIn(applyApplicationIds).stream()
        .map(domainEntityMapper::toDomain)
        .toList();
  }
}
