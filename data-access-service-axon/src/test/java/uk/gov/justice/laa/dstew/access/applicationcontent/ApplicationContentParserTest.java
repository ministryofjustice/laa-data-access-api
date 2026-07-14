package uk.gov.justice.laa.dstew.access.applicationcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplicationContentParserTest {

  private final ApplicationContentParser parser =
      new ApplicationContentParser(
          new PayloadValidator(
              new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator()));

  @Test
  void givenCompleteApplicationContent_whenParse_thenExtractsProductionDetails() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Map<String, Object> rawContent =
        Map.of(
            "id",
            applyApplicationId.toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "office",
            Map.of("code", "OFF1"),
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    proceedingId.toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Test proceeding",
                    "matterTypeEnum",
                    " special_children_act ",
                    "categoryOfLawEnum",
                    "family",
                    "usedDelegatedFunctions",
                    true)),
            "allLinkedApplications",
            List.of(
                Map.of(
                    "leadApplicationId",
                    UUID.randomUUID().toString(),
                    "associatedApplicationId",
                    linkedApplicationId.toString())));

    ParsedAppContentDetails result = parser.parse(rawContent);

    assertThat(result.applyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(result.categoryOfLaw()).isEqualTo(CategoryOfLaw.FAMILY);
    assertThat(result.matterType()).isEqualTo(MatterType.SPECIAL_CHILDREN_ACT);
    assertThat(result.submittedAt()).isEqualTo(Instant.parse("2026-01-15T10:20:30Z"));
    assertThat(result.officeCode()).isEqualTo("OFF1");
    assertThat(result.usedDelegatedFunctions()).isTrue();
    assertThat(result.proceedings())
        .singleElement()
        .extracting(Proceeding::getId)
        .isEqualTo(proceedingId);
    assertThat(result.allLinkedApplications())
        .singleElement()
        .extracting(LinkedApplication::getAssociatedApplicationId)
        .isEqualTo(linkedApplicationId);
  }

  @Test
  void givenProceedingsWithoutLead_whenParse_thenThrowsProductionValidationFailure() {
    Map<String, Object> rawContent =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "leadProceeding",
                    false,
                    "description",
                    "Test proceeding")));

    assertThatThrownBy(() -> parser.parse(rawContent))
        .isInstanceOf(ValidationException.class)
        .extracting("errors")
        .isEqualTo(List.of("No lead proceeding found in application content"));
  }

  @Test
  void givenUnparseableSubmittedAt_whenParse_thenThrowsDateTimeParseException() {
    Map<String, Object> rawContent =
        Map.of("id", UUID.randomUUID().toString(), "submittedAt", "not-an-instant");

    assertThatThrownBy(() -> parser.parse(rawContent))
        .isInstanceOf(java.time.format.DateTimeParseException.class);
  }

  @Test
  void givenUndeclaredApplicationAndNestedFields_whenParse_thenPreservesAdditionalContent() {
    Map<String, Object> rawContent =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "futureApplicationField",
            "application-value",
            "applicant",
            Map.of("futureApplicantField", "applicant-value"));

    ApplicationContent content =
        new PayloadValidator(
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator())
            .convertAndValidate(rawContent, ApplicationContent.class);

    assertThat(content.getAdditionalApplicationContent())
        .containsEntry("futureApplicationField", "application-value");
    assertThat(content.getApplicant().getAdditionalContent())
        .containsEntry("futureApplicantField", "applicant-value");
  }
}
