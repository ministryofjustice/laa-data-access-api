package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service class for parsing and normalising application content.
 */
@Service
public class ApplicationContentParserService {

  private static final MatterTypeConvertor matterTypeDeserializer = new MatterTypeConvertor();
  private static final CategoryOfLawTypeConvertor categoryOfLawTypeDeserializer = new CategoryOfLawTypeConvertor();


  /**
   * Normalises application content details from the create request.
   *
   * @param requestAppContent the application create request
   * @return the extracted application content details
   */
  public ParsedAppContentDetails normaliseApplicationContentDetails(
      RequestApplicationContent requestAppContent) {

    return processingApplicationContent(requestAppContent.getApplicationContent());

  }

  /**
   * Processes application content to extract and set key fields in the entity.
   *
   * @param applicationContent the application content to process
   * @return the extracted application content details
   */
  private static ParsedAppContentDetails processingApplicationContent(
      ApplicationContent applicationContent) {
    if (applicationContent == null) {
      throw new ValidationException(List.of("Application content is null"));
    }
    if (applicationContent.getProceedings() == null
        || applicationContent.getProceedings().isEmpty()) {
      throw new ValidationException(List.of("No proceedings found in application content"));
    }
    List<Proceeding> proceedingList = applicationContent.getProceedings().stream()
        .filter(Objects::nonNull).toList();
    Proceeding leadProceeding = proceedingList
        .stream()
        .filter(proceeding -> proceeding.getLeadProceeding().equals(Boolean.TRUE))
        .findFirst()
        .orElseThrow(() -> new ValidationException(List.of("No lead proceeding found in application content")));
    boolean usedDelegatedFunction =
        proceedingList
            .stream()
            .filter(proceeding -> null != proceeding.getUsedDelegatedFunctions())
            .anyMatch(Proceeding::getUsedDelegatedFunctions);

    String officeCode = (applicationContent.getOffice() == null) ? null : applicationContent.getOffice().getCode();

    return ParsedAppContentDetails
        .builder()
        .applyApplicationId(applicationContent.getId())
        .categoryOfLaw(categoryOfLawTypeDeserializer.lenientEnumConversion(leadProceeding.getCategoryOfLaw()))
        .matterType(matterTypeDeserializer.lenientEnumConversion(leadProceeding.getMatterType()))
        .submittedAt(Instant.parse(applicationContent.getSubmittedAt()))
        .usedDelegatedFunctions(usedDelegatedFunction)
        .allLinkedApplications(
            (List<Map<String, Object>>) applicationContent
                .getAdditionalApplicationContent()
                .get("allLinkedApplications")
        )
        .officeCode(officeCode)
        .build();
  }


}
