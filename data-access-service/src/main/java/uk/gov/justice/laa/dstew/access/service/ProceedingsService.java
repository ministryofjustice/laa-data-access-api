package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Processor for handling proceedings related operations.
 */
@Component
public class ProceedingsService {
  private final ProceedingMapper proceedingsMapper;
  private final ProceedingRepository proceedingRepository;

  public ProceedingsService(ProceedingMapper proceedingsMapper, ProceedingRepository proceedingRepository) {
    this.proceedingsMapper = proceedingsMapper;
    this.proceedingRepository = proceedingRepository;
  }

  /**
   * Saves proceedings from application content to the repository.
   *
   * @param applicationContent the application content containing proceedings
   * @param id                 the associated application ID
   */
  public void saveProceedings(ApplicationContent applicationContent, UUID id) {
    List<ProceedingEntity> proceedingEntities = applicationContent.getProceedings().stream()
        .map(proceeding -> proceedingsMapper.toProceedingEntity(proceeding, id)).toList();
    boolean hasLeadProceeding = proceedingEntities.stream().anyMatch(ProceedingEntity::isLead);
    if (!hasLeadProceeding) {
      throw new ValidationException(List.of("No lead proceedings found"));
    }
    proceedingRepository.saveAll(proceedingEntities);
  }

}
