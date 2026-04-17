package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplication;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Service class for parsing and normalising application content. */
@Service
public class ApplicationContentParserService {

  private static final MatterTypeConvertor matterTypeDeserializer = new MatterTypeConvertor();
  private static final CategoryOfLawTypeConvertor categoryOfLawTypeDeserializer =
      new CategoryOfLawTypeConvertor();

  private final ObjectMapper objectMapper;

  public ApplicationContentParserService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Normalises application content details from the create request.
   *
   * @param applicationContent the application create request
   * @return the extracted application content details
   */
  public ParsedAppContentDetails normaliseApplicationContentDetails(
      ApplicationContent applicationContent) {

    return processingApplicationContent(applicationContent);
  }

  /**
   * Parses application content from a raw map, returning domain types. ApplicationContent (model)
   * is used internally only and never exposed to callers.
   *
   * @param applicationContentMap the raw content map from the command
   * @return domain ParsedAppContentDetails
   */
  public uk.gov.justice.laa.dstew.access.domain.ParsedAppContentDetails parseFromMap(
      Map<String, Object> applicationContentMap) {
    ApplicationContent content =
        objectMapper.convertValue(applicationContentMap, ApplicationContent.class);
    ParsedAppContentDetails legacy = processingApplicationContent(content);
    return new uk.gov.justice.laa.dstew.access.domain.ParsedAppContentDetails(
        legacy.applyApplicationId(),
        toDomainCategoryOfLaw(legacy.categoryOfLaw()),
        toDomainMatterType(legacy.matterType()),
        legacy.submittedAt(),
        legacy.officeCode(),
        legacy.usedDelegatedFunctions(),
        toDomainLinkedApplications(content));
  }

  private uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw toDomainCategoryOfLaw(
      CategoryOfLaw col) {
    if (col == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw.valueOf(col.name());
  }

  private uk.gov.justice.laa.dstew.access.domain.MatterType toDomainMatterType(MatterType mt) {
    if (mt == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.domain.MatterType.valueOf(mt.name());
  }

  private List<LinkedApplication> toDomainLinkedApplications(ApplicationContent content) {
    if (content.getAllLinkedApplications() == null) {
      return null;
    }
    return content.getAllLinkedApplications().stream()
        .map(
            la -> new LinkedApplication(la.getLeadApplicationId(), la.getAssociatedApplicationId()))
        .collect(Collectors.toList());
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
    Proceeding leadProceeding = null;
    Boolean usedDelegatedFunction = null;
    if (applicationContent.getProceedings() != null
        && !applicationContent.getProceedings().isEmpty()) {
      List<Proceeding> proceedingList =
          applicationContent.getProceedings().stream().filter(Objects::nonNull).toList();
      leadProceeding =
          proceedingList.stream()
              .filter(proceeding -> proceeding.getLeadProceeding().equals(Boolean.TRUE))
              .findFirst()
              .orElseThrow(
                  () ->
                      new ValidationException(
                          List.of("No lead proceeding found in application content")));
      List<Boolean> usedDelegatedFunctionValues =
          proceedingList.stream()
              .map(Proceeding::getUsedDelegatedFunctions)
              .filter(Objects::nonNull)
              .toList();
      if (!usedDelegatedFunctionValues.isEmpty()) {
        usedDelegatedFunction =
            usedDelegatedFunctionValues.stream().anyMatch(Boolean::booleanValue);
      }
    }

    String officeCode =
        (applicationContent.getOffice() == null) ? null : applicationContent.getOffice().getCode();

    return ParsedAppContentDetails.builder()
        .applyApplicationId(applicationContent.getId())
        .categoryOfLaw(getCategoryOfLaw(leadProceeding))
        .matterType(getMatterType(leadProceeding))
        .submittedAt(Instant.parse(applicationContent.getSubmittedAt()))
        .usedDelegatedFunctions(usedDelegatedFunction)
        .officeCode(officeCode)
        .build();
  }

  private static MatterType getMatterType(Proceeding leadProceeding) {
    if (Objects.isNull(leadProceeding)) {
      return null;
    }
    return matterTypeDeserializer.lenientEnumConversion(leadProceeding.getMatterType());
  }

  private static CategoryOfLaw getCategoryOfLaw(Proceeding leadProceeding) {
    if (Objects.isNull(leadProceeding)) {
      return null;
    }
    return categoryOfLawTypeDeserializer.lenientEnumConversion(leadProceeding.getCategoryOfLaw());
  }
}
