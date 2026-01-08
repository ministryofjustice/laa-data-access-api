package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.Proceeding;

@Service
public class ApplicationMapperService {

  private final ApplicationMapper applicationMapper;
  private final ObjectMapper objectMapper;

  public ApplicationMapperService(final ApplicationMapper applicationMapper, final ObjectMapper objectMapper) {
    this.applicationMapper = applicationMapper;
    this.objectMapper = objectMapper;
  }

  public ApplicationEntity toApplicationEntity(ApplicationCreateRequest req, Integer applicationVersion) {
    ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    ApplicationContent applicationContent = objectMapper.convertValue(req.getApplicationContent(), ApplicationContent.class);
    processingApplicationContent(entity, applicationContent);
    entity.setSchemaVersion(applicationVersion);
    return entity;
  }

  /**
   * Processes application content to extract and set key fields in the entity.
   *
   * @param entity             the application entity to update
   * @param applicationContent the application content to process
   */
  private void processingApplicationContent(ApplicationEntity entity, ApplicationContent applicationContent) {
    if (applicationContent == null) {
      return;
    }
    if (applicationContent.getProceedings() == null || applicationContent.getProceedings().isEmpty()) {
      return;
    }
    Proceeding leadProceeding = applicationContent.getProceedings().stream()
        .filter(Objects::nonNull)
        .filter(Proceeding::leadProceeding)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No lead proceeding found in application content"));
    boolean usedDelegatedFunction =
        applicationContent.getProceedings().stream().filter(Objects::nonNull)
            .anyMatch(Proceeding::useDelegatedFunctions);
    entity.setAutoGranted(applicationContent.isAutoGrant());
    entity.setUseDelegatedFunctions(usedDelegatedFunction);
    entity.setCategoryOfLaw(leadProceeding.categoryOfLaw());
    entity.setMatterType(leadProceeding.matterType());
  }
}
