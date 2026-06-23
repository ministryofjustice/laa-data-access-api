package uk.gov.justice.laa.dstew.access.usecase.shared.parser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Validates a raw application-content map and extracts parsed details. Pure Java — no Spring
 * annotations. Wired explicitly via CreateApplicationConfig.
 */
public class ApplicationContentParser {

  private static final MatterTypeConvertor matterTypeConvertor = new MatterTypeConvertor();
  private static final CategoryOfLawTypeConvertor categoryOfLawConvertor =
      new CategoryOfLawTypeConvertor();

  private final PayloadValidator payloadValidator;

  /**
   * Constructs the parser with the validation service used to deserialise and validate the raw
   * content map before parsing.
   *
   * @param payloadValidationService the service used to convert and validate the raw payload
   */
  public ApplicationContentParser(PayloadValidator payloadValidationService) {
    this.payloadValidator = payloadValidationService;
  }

  /**
   * Validates {@code rawContent} against {@link ApplicationContent} constraints, then parses and
   * returns extracted details including proceedings and linked applications.
   *
   * @param rawContent the raw application-content map from the command
   * @return parsed details
   * @throws ValidationException if validation or parsing fails
   */
  public ParsedAppContentDetails parse(Map<String, Object> rawContent) {
    ApplicationContent content =
        payloadValidator.convertAndValidate(rawContent, ApplicationContent.class);
    return parseValidated(content);
  }

  private ParsedAppContentDetails parseValidated(ApplicationContent applicationContent) {
    Proceeding leadProceeding = null;
    Boolean usedDelegatedFunction = null;

    if (applicationContent.getProceedings() != null
        && !applicationContent.getProceedings().isEmpty()) {
      List<Proceeding> proceedingList =
          applicationContent.getProceedings().stream().filter(Objects::nonNull).toList();
      leadProceeding =
          proceedingList.stream()
              .filter(p -> Boolean.TRUE.equals(p.getLeadProceeding()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new ValidationException(
                          List.of("No lead proceeding found in application content")));
      List<Boolean> udfs =
          proceedingList.stream()
              .map(Proceeding::getUsedDelegatedFunctions)
              .filter(Objects::nonNull)
              .toList();
      if (!udfs.isEmpty()) {
        usedDelegatedFunction = udfs.stream().anyMatch(Boolean::booleanValue);
      }
    }

    String officeCode =
        (applicationContent.getOffice() == null) ? null : applicationContent.getOffice().getCode();

    List<Proceeding> proceedings =
        applicationContent.getProceedings() != null
            ? applicationContent.getProceedings()
            : Collections.emptyList();

    List<LinkedApplication> allLinkedApplications =
        applicationContent.getAllLinkedApplications() != null
            ? applicationContent.getAllLinkedApplications()
            : Collections.emptyList();

    return ParsedAppContentDetails.builder()
        .applyApplicationId(applicationContent.getId())
        .categoryOfLaw(getCategoryOfLaw(leadProceeding))
        .matterType(getMatterType(leadProceeding))
        .submittedAt(Instant.parse(applicationContent.getSubmittedAt()))
        .usedDelegatedFunctions(usedDelegatedFunction)
        .officeCode(officeCode)
        .proceedings(proceedings)
        .allLinkedApplications(allLinkedApplications)
        .build();
  }

  private MatterType getMatterType(Proceeding leadProceeding) {
    if (leadProceeding == null) {
      return null;
    }
    return matterTypeConvertor.lenientEnumConversion(leadProceeding.getMatterTypeEnum());
  }

  private CategoryOfLaw getCategoryOfLaw(Proceeding leadProceeding) {
    if (leadProceeding == null) {
      return null;
    }
    return categoryOfLawConvertor.lenientEnumConversion(leadProceeding.getCategoryOfLawEnum());
  }
}
