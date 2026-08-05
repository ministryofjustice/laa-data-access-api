package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator();

  @Test
  void givenApplyPayloadAndVersionOneSchema_whenValidate_thenAcceptsPayload() {
    validator.validate(validApplyPayload(), "ApplyApplication.json", 1);
  }

  @Test
  void givenApplyPayloadMissingRequiredId_whenValidate_thenReportsProductionFailureShape() {
    Map<String, Object> payload = new HashMap<>(validApplyPayload());
    payload.remove("id");

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("id")));
  }

  @Test
  void givenInvalidApplicationId_whenValidate_thenReportsUuidFormat() {
    Map<String, Object> payload = new HashMap<>(validApplyPayload());
    payload.put("id", "not-a-uuid");

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("id")));
  }

  @Test
  void givenEmptyProceedingsForVersionOne_whenValidate_thenReportsMinimumItems() {
    Map<String, Object> payload = new HashMap<>(validApplyPayload());
    payload.put("proceedings", List.of());

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("proceedings")));
  }

  @Test
  void givenProceedingMissingDescription_whenValidate_thenReportsNestedRequiredField() {
    Map<String, Object> payload = new HashMap<>(validApplyPayload());
    Map<String, Object> proceeding = new HashMap<>(validProceedingPayload());
    proceeding.remove("description");
    payload.put("proceedings", List.of(proceeding));

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("description")));
  }

  private Map<String, Object> validApplyPayload() {
    return Map.of(
        "id", UUID.randomUUID().toString(),
        "createdAt", "2026-01-15T09:20:30Z",
        "submittedAt", "2026-01-15T10:20:30Z",
        "provider", Map.of("officeCode", "OFF1", "contactEmail", "provider@example.com"),
        "client",
            Map.of(
                "firstName", "Ada",
                "lastName", "Lovelace",
                "dateOfBirth", "1815-12-10",
                "appliedPreviously", false,
                "correspondenceAddress", List.of(Map.of("location", "HOME"))),
        "allLinkedApplications", List.of(),
        "proceedings", List.of(validProceedingPayload()));
  }

  private Map<String, Object> validProceedingPayload() {
    return Map.ofEntries(
        Map.entry("id", UUID.randomUUID().toString()),
        Map.entry("leadProceeding", true),
        Map.entry("ccmsCode", "SE003"),
        Map.entry("meaning", "Care order"),
        Map.entry("description", "Test proceeding"),
        Map.entry("matterType", "SPECIAL_CHILDREN_ACT"),
        Map.entry("categoryOfLaw", "FAMILY"),
        Map.entry("clientInvolvementType", "A"),
        Map.entry("usedDelegatedFunctions", false),
        Map.entry("delegatedFunctionsCostLimitation", 0),
        Map.entry("substantiveCostLimitation", 2500),
        Map.entry("levelOfService", "FULL"),
        Map.entry(
            "scopeLimitations",
            List.of(
                Map.of(
                    "id", UUID.randomUUID().toString(),
                    "scopeType", "LIMITATION",
                    "scopeLimitation", "CV117",
                    "scopeDescription", "Final hearing"))));
  }
}
