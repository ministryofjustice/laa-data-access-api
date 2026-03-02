package uk.gov.justice.laa.dstew.access.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        "office", Map.of("code", "OFF1"),
        "proceedings", List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "leadProceeding", true,
                "description", "Test proceeding"
            )
        )
    );


    validator.validate(payload, "ApplyApplication.json", 1);

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
}