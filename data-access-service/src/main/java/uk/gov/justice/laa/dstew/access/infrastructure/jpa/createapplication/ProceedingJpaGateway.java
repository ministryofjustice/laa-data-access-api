package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ProceedingGateway;

/** JPA implementation of ProceedingGateway. Wired via CreateApplicationConfig (no @Component). */
public class ProceedingJpaGateway implements ProceedingGateway {

  private final ProceedingRepository proceedingRepository;
  private final ProceedingGatewayMapper mapper;

  public ProceedingJpaGateway(
      ProceedingRepository proceedingRepository, ProceedingGatewayMapper mapper) {
    this.proceedingRepository = proceedingRepository;
    this.mapper = mapper;
  }

  @Override
  public void saveAll(UUID applicationId, List<ProceedingDomain> proceedings) {
    if (proceedings == null || proceedings.isEmpty()) {
      return;
    }
    List<ProceedingEntity> entities =
        proceedings.stream().map(p -> mapper.toEntity(p, applicationId)).toList();
    proceedingRepository.saveAll(entities);
  }
}
