package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
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
   * @param application        the associated application entity
   */
  public void saveProceedings(ApplicationContent applicationContent, ApplicationEntity application) {
    List<ProceedingEntity> proceedingEntities = applicationContent.getProceedings().stream()
        .map(proceeding -> proceedingMapper.toProceedingEntity(proceeding, application)).toList();
    proceedingRepository.saveAll(proceedingEntities);
  }

}
