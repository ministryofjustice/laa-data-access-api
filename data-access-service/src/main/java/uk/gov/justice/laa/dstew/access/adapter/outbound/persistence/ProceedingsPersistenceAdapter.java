package uk.gov.justice.laa.dstew.access.adapter.outbound.persistence;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.ProceedingsPersistencePort;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.service.ProceedingsService;

/**
 * Adapter that bridges the domain {@link ProceedingsPersistencePort} to the existing {@link
 * ProceedingsService}.
 */
@Component
@RequiredArgsConstructor
public class ProceedingsPersistenceAdapter implements ProceedingsPersistencePort {

  private final ProceedingsService proceedingsService;
  private final ObjectMapper objectMapper;

  @Override
  public void saveProceedings(Map<String, Object> applicationContent, UUID applicationId) {
    ApplicationContent content =
        objectMapper.convertValue(applicationContent, ApplicationContent.class);
    proceedingsService.saveProceedings(content, applicationId);
  }
}
