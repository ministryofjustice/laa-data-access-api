package uk.gov.justice.laa.dstew.access.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private JsonSchemaValidator validator;

  @BeforeEach
  void setUp() {
    validator = new JsonSchemaValidator();
  }

  @Test
  void validateAcceptsPayloadMatchingSchema() {
    Map<String, Object> payload = Map.of(
        "id", UUID.randomUUID().toString(),
        "submittedAt", "2026-01-15T10:20:30Z",
        "status", "APPLICATION_IN_PROGRESS",
        "laaReference", "REF-123",
        "office", Map.of("code", "OFF1")

    );


    validator.validate(payload, "ApplyApplication.json", 1);

  }

  @Test
  void validateAcceptsPayloadMatchingSchema_Css() {
    Map<String, Object> payload = Map.of(
        "id", UUID.randomUUID().toString(),
        "submittedAt", "2026-01-15T10:20:30Z",
        "status", "APPLICATION_IN_PROGRESS",
        "laaReference", "REF-123",
        "office", Map.of("code", "OFF1"),
        "proceedings", List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "leadProceeding", true,
                "description", "Test proceeding"
            )
        )
    );


    validator.validate(payload, "CssApplication.json", 1);

  }

  @Test
  void validateRejectsMissingRequiredField_Css() {
    Map<String, Object> payload = Map.of(
        "id", UUID.randomUUID().toString(),
        "submittedAt", "2026-01-15T10:20:30Z",
        "status", "APPLICATION_IN_PROGRESS",
        "office", Map.of("code", "OFF1")
    );

    ValidationException ex = assertThrows(ValidationException.class,
        () -> validator.validate(payload, "CssApplication.json", 1));

    assertTrue(ex.errors().stream().anyMatch(msg -> msg.contains("laaReference")),
        "Expected validation errors to mention laaReference field format");
  }

  @Test
  void validateExceptionOnInvalidUUIDPayloadMatchingSchema() {
    Map<String, Object> payload = Map.of(
        "id", "test-id-not-uuid",
        "submittedAt", "2026-01-15T10:20:30Z",
        "status", "APPLICATION_IN_PROGRESS",
        "laaReference", "REF-123",
        "office", Map.of("code", "OFF1"),
        "proceedings", List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "leadProceeding", true,
                "description", "Test proceeding"
            )
        )
    );

    ValidationException validationException =
        assertThrows(ValidationException.class, () -> validator.validate(payload, "ApplyApplication.json", 1));
    assertTrue(validationException.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("id")),
        "Expected validation errors to mention id field format");
  }

  @Test
  void validateRejectsMissingRequiredField() {
    Map<String, Object> payloadMissingId = Map.of(
        "submittedAt", "2026-01-15T10:20:30Z",
        "proceedings", List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "leadProceeding", true,
                "description", "Test proceeding"
            )
        )
    );

    ValidationException ex = assertThrows(ValidationException.class,
        () -> validator.validate(payloadMissingId, "ApplyApplication.json", 1));

    assertTrue(ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("id")),
        "Expected validation errors to mention missing id");
  }

  @Test
  void validateRejectsEmptyArrayWhenMinItemsRequired() {
    Map<String, Object> payload = Map.of(
        "id", UUID.randomUUID().toString(),
        "submittedAt", "2026-01-15T10:20:30Z",
        "status", "APPLICATION_IN_PROGRESS",
        "office", Map.of("code", "OFF1"),
        "proceedings", Collections.emptyList()
    );

    ValidationException ex = assertThrows(ValidationException.class,
        () -> validator.validate(payload, "ApplyApplication.json", 2));

    // Debug: print all errors to understand the structure
    ex.errors().forEach(error -> System.out.println("Error: " + error));

    assertTrue(ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("proceedings")),
        "Expected validation errors to mention proceedings field. Actual errors: " + ex.errors());
  }
}