package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;

/**
 * Processor for handling proceedings related operations.
 */
@Component
public class ProceedingsService {
  private final ProceedingMapper proceedingMapper;
  private final ProceedingRepository proceedingRepository;

  public ProceedingsService(ProceedingMapper proceedingMapper, ProceedingRepository proceedingRepository) {
    this.proceedingMapper = proceedingMapper;
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
        .map(proceeding -> proceedingMapper.toProceedingEntity(proceeding, id)).toList();
    proceedingRepository.saveAll(proceedingEntities);
  }

}
