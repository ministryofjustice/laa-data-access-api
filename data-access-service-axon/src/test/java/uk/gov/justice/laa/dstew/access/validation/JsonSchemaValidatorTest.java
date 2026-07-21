package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator();

  @Test
  void givenApplyPayloadAndVersionTwoSchema_whenValidate_thenAcceptsPayload() {
    Map<String, Object> payload =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "status",
            "APPLICATION_IN_PROGRESS",
            "office",
            Map.of("code", "OFF1"),
            "applicant",
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "addresses",
                List.of(Map.of("id", UUID.randomUUID().toString()))),
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Test proceeding")));

    validator.validate(payload, "ApplyApplication.json", 2);
  }

  @Test
  void givenCssPayloadAndCssSchema_whenValidate_thenAcceptsPayload() {
    Map<String, Object> payload =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "status",
            "APPLICATION_IN_PROGRESS",
            "laaReference",
            "REF-123",
            "office",
            Map.of("code", "OFF1"),
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Test proceeding")));

    validator.validate(payload, "CssApplication.json", 1);
  }

  @Test
  void givenApplyPayloadMissingRequiredId_whenValidate_thenReportsProductionFailureShape() {
    Map<String, Object> payload = Map.of("submittedAt", "2026-01-15T10:20:30Z");

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("id")));
  }

  @Test
  void givenCssPayloadMissingLaaReference_whenValidate_thenReportsRequiredField() {
    Map<String, Object> payload =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "status",
            "APPLICATION_IN_PROGRESS",
            "office",
            Map.of("code", "OFF1"));

    assertThatThrownBy(() -> validator.validate(payload, "CssApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.contains("laaReference")));
  }

  @Test
  void givenInvalidApplicationId_whenValidate_thenReportsUuidFormat() {
    Map<String, Object> payload =
        Map.of(
            "id",
            "not-a-uuid",
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "office",
            Map.of("code", "OFF1"));

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("id")));
  }

  @Test
  void givenEmptyProceedingsForVersionTwo_whenValidate_thenReportsMinimumItems() {
    Map<String, Object> payload =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "office",
            Map.of("code", "OFF1"),
            "applicant",
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "addresses",
                List.of(Map.of("id", UUID.randomUUID().toString()))),
            "proceedings",
            Collections.emptyList());

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 2))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("proceedings")));
  }

  @Test
  void givenProceedingMissingDescription_whenValidate_thenReportsNestedRequiredField() {
    Map<String, Object> payload =
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "proceedings",
            List.of(Map.of("id", UUID.randomUUID().toString(), "leadProceeding", true)));

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("description")));
  }
}
