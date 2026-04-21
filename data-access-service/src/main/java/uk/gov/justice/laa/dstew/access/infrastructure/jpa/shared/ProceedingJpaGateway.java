package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;

/** JPA implementation of ProceedingGateway. Wired via config (no @Component). */
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

  @Override
  public List<ProceedingDomain> findAllByIds(UUID applicationId, List<UUID> proceedingIds) {
    List<UUID> idsToFetch = proceedingIds.stream().distinct().toList();
    List<ProceedingEntity> proceedings = proceedingRepository.findAllById(idsToFetch);

    List<UUID> foundProceedingIds = proceedings.stream().map(ProceedingEntity::getId).toList();

    String proceedingIdsNotFound =
        idsToFetch.stream()
            .filter(id -> !foundProceedingIds.contains(id))
            .map(UUID::toString)
            .collect(Collectors.joining(","));

    String proceedingIdsNotLinkedToApplication =
        proceedings.stream()
            .filter(p -> !p.getApplicationId().equals(applicationId))
            .map(p -> p.getId().toString())
            .collect(Collectors.joining(","));

    if (!proceedingIdsNotFound.isEmpty() || !proceedingIdsNotLinkedToApplication.isEmpty()) {
      List<String> errors = new ArrayList<>();
      if (!proceedingIdsNotFound.isEmpty()) {
        errors.add("No proceeding found with id: " + proceedingIdsNotFound);
      }
      if (!proceedingIdsNotLinkedToApplication.isEmpty()) {
        errors.add("Not linked to application: " + proceedingIdsNotLinkedToApplication);
      }
      throw new ResourceNotFoundException(String.join("; ", errors));
    }

    return proceedings.stream().map(mapper::toDomain).toList();
  }
}
