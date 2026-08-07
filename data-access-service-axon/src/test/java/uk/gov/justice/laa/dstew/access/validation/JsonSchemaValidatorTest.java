package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validProceedingContent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator();

  @Test
  void givenApplyPayloadAndVersionOneSchema_whenValidate_thenAcceptsPayload() {
    validator.validate(
        validApplicationContent(UUID.randomUUID(), UUID.randomUUID()), "ApplyApplication.json", 1);
  }

  @Test
  void givenApplyPayloadMissingRequiredId_whenValidate_thenReportsProductionFailureShape() {
    Map<String, Object> payload =
        new HashMap<>(validApplicationContent(UUID.randomUUID(), UUID.randomUUID()));
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
    Map<String, Object> payload =
        new HashMap<>(validApplicationContent(UUID.randomUUID(), UUID.randomUUID()));
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
    Map<String, Object> payload =
        new HashMap<>(validApplicationContent(UUID.randomUUID(), UUID.randomUUID()));
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
    Map<String, Object> payload =
        new HashMap<>(validApplicationContent(UUID.randomUUID(), UUID.randomUUID()));
    Map<String, Object> proceeding = new HashMap<>(validProceedingContent(UUID.randomUUID()));
    proceeding.remove("description");
    payload.put("proceedings", List.of(proceeding));

    assertThatThrownBy(() -> validator.validate(payload, "ApplyApplication.json", 1))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .anyMatch(message -> message.toLowerCase().contains("description")));
  }
}
