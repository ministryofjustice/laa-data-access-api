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
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Map<String, Object> rawContent =
        Map.of(
            "id",
            applicationId.toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "provider",
            Map.of("officeCode", "OFF1", "contactEmail", "test@example.com"),
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    proceedingId.toString(),
                    "leadProceeding",
                    true,
                    "code",
                    "SE003",
                    "meaning",
                    "Care order",
                    "description",
                    "Test proceeding",
                    "matterType",
                    "SPECIAL_CHILDREN_ACT",
                    "categoryOfLaw",
                    "Family",
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

    assertThat(result.categoryOfLaw()).isEqualTo("Family");
    assertThat(result.matterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(result.submittedAt()).isEqualTo(Instant.parse("2026-01-15T10:20:30Z"));
    assertThat(result.provider()).isNotNull();
    assertThat(result.provider().getOfficeCode()).isEqualTo("OFF1");
    assertThat(result.provider().getContactEmail()).isEqualTo("test@example.com");
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
                    "code",
                    "SE003",
                    "description",
                    "Test proceeding")));

    assertThatThrownBy(() -> parser.parse(rawContent))
        .isInstanceOf(ValidationException.class)
        .extracting("errors")
        .isEqualTo(List.of("No lead proceeding found in application content"));
  }

  @Test
  void givenUnparseableSubmittedAt_whenParse_thenThrowsValidationFailure() {
    Map<String, Object> rawContent =
        Map.of("id", UUID.randomUUID().toString(), "submittedAt", "not-an-instant");

    assertThatThrownBy(() -> parser.parse(rawContent))
        .isInstanceOf(ValidationException.class)
        .extracting("errors")
        .isEqualTo(List.of("submittedAt: must be an ISO-8601 instant"));
  }
}
