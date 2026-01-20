package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service class for parsing and normalising application content.
 */
@Service
public class ApplicationContentParserService {

  private final ObjectMapper objectMapper;

  public ApplicationContentParserService(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Normalises application content details from the create request.
   *
   * @param appContentMap the application create request
   * @return the extracted application content details
   */
  public ParsedAppContentDetails normaliseApplicationContentDetails(
      Map<String, Object> appContentMap) {
    ApplicationContentDetails
        applicationContentDetails = objectMapper.convertValue(appContentMap, ApplicationContentDetails.class);
    return processingApplicationContent(applicationContentDetails);

  }

  /**
   * Processes application content to extract and set key fields in the entity.
   *
   * @param applicationContent the application content to process
   * @return the extracted application content details
   */
  private static ParsedAppContentDetails processingApplicationContent(ApplicationContentDetails applicationContent) {
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
    return ParsedAppContentDetails
        .builder()
        .applyApplicationId(applicationContent.getId())
        .categoryOfLaw(leadProceeding.categoryOfLaw())
        .matterType(leadProceeding.matterType())
        .submittedAt(applicationContent.getSubmittedAt())
        .useDelegatedFunctions(usedDelegatedFunction)
        .build();
  }
}
