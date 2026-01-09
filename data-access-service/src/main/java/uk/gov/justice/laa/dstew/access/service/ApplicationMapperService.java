package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service for mapping application requests to application entities.
 * Handles the conversion and processing of application content.
 */
@Service
public class ApplicationMapperService {

  private final ApplicationMapper applicationMapper;
  private final ObjectMapper objectMapper;

  public ApplicationMapperService(final ApplicationMapper applicationMapper, final ObjectMapper objectMapper) {
    this.applicationMapper = applicationMapper;
    this.objectMapper = objectMapper;
  }

  /**
   * Converts an ApplicationCreateRequest to an ApplicationEntity.
   *
   * @param req                the application create request
   * @param applicationVersion the version of the application schema
   * @return the mapped application entity
   */
  public ApplicationEntity toApplicationEntity(ApplicationCreateRequest req, Integer applicationVersion) {
    ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    ApplicationContentDetails
        applicationContentDetails = objectMapper.convertValue(req.getApplicationContent(), ApplicationContentDetails.class);
    processingApplicationContent(entity, applicationContentDetails);
    entity.setSchemaVersion(applicationVersion);
    return entity;
  }

  /**
   * Processes application content to extract and set key fields in the entity.
   *
   * @param entity             the application entity to update
   * @param applicationContent the application content to process
   */
  private void processingApplicationContent(ApplicationEntity entity, ApplicationContentDetails applicationContent) {
    if (applicationContent.getProceedings() == null || applicationContent.getProceedings().isEmpty()) {
      throw new ValidationException(List.of("No proceedings found in application content"));
    }
    ProceedingDetails leadProceeding = applicationContent.getProceedings().stream()
        .filter(Objects::nonNull)
        .filter(ProceedingDetails::leadProceeding)
        .findFirst()
        .orElseThrow(() -> new ValidationException(List.of("No lead proceeding found in application content")));
    boolean usedDelegatedFunction =
        applicationContent.getProceedings().stream()
            .filter(Objects::nonNull)
            .filter(proceeding -> null != proceeding.useDelegatedFunctions())
            .anyMatch(ProceedingDetails::useDelegatedFunctions);
    entity.setAutoGranted(applicationContent.isAutoGrant());
    entity.setUseDelegatedFunctions(usedDelegatedFunction);
    entity.setCategoryOfLaw(leadProceeding.categoryOfLaw());
    entity.setMatterType(leadProceeding.matterType());
    entity.setSubmittedAt(applicationContent.getSubmittedAt());
  }
}
