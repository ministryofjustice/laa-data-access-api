package uk.gov.justice.laa.dstew.access.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator();

  @Test
  void validateAcceptsPayloadMatchingSchema() {
    Map<String, Object> payload =
        Map.of(
            "submittedAt", "2026-01-15T10:20:30Z",
            "status", "APPLICATION_IN_PROGRESS",
            "laaReference", "REF-123",
            "office", Map.of("code", "OFF1"));

    validator.validate(payload, "ApplyApplication.json", 1);
  }

  @Test
  void givenValidV2Payload_whenValidate_thenAcceptsPayload() {
    Map<String, Object> payload = validApplyApplicationV2Payload();
    validator.validate(payload, "ApplyApplication.json", 2);
  }

  @Test
  void givenV2PayloadMissingRequiredField_whenValidate_thenThrowsValidationException() {
    Map<String, Object> payload = new HashMap<>(validApplyApplicationV2Payload());
    payload.remove("provider");

    ValidationException ex =
        assertThrows(
            ValidationException.class,
            () -> validator.validate(payload, "ApplyApplication.json", 2));

    assertTrue(
        ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("provider")),
        "Expected validation errors to mention missing provider field");
  }

  @Test
  void validateRejectsMissingRequiredField() {
    Map<String, Object> payloadMissingSubmittedAt =
        Map.of(
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Test proceeding")));

    ValidationException ex =
        assertThrows(
            ValidationException.class,
            () -> validator.validate(payloadMissingSubmittedAt, "ApplyApplication.json", 1));

    assertTrue(
        ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("submittedat")),
        "Expected validation errors to mention missing submittedAt");
  }

  @Test
  void validateRejectsEmptyArrayWhenMinItemsRequired() {
    Map<String, Object> payload = new HashMap<>(validApplyApplicationV2Payload());
    payload.put("proceedings", Collections.emptyList());

    ValidationException ex =
        assertThrows(
            ValidationException.class,
            () -> validator.validate(payload, "ApplyApplication.json", 2));

    assertTrue(
        ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("proceedings")),
        "Expected validation errors to mention proceedings field. Actual errors: " + ex.errors());
  }

  private Map<String, Object> validApplyApplicationV2Payload() {
    return Map.of(
        "createdAt",
        "2026-01-15T09:00:00Z",
        "submittedAt",
        "2026-01-15T10:20:30Z",
        "status",
        "APPLICATION_IN_PROGRESS",
        "provider",
        Map.of("officeCode", "OFF1", "contactEmail", "provider@example.com"),
        "allLinkedApplications",
        List.of(
            Map.of(
                "id",
                "550e8400-e29b-41d4-a716-446655440000",
                "leadApplicationId",
                "550e8400-e29b-41d4-a716-446655440000",
                "associatedApplicationId",
                "550e8400-e29b-41d4-a716-446655440000",
                "targetApplicationId",
                "550e8400-e29b-41d4-a716-446655440000",
                "linkTypeCode",
                "example-string",
                "createdAt",
                "2024-03-21T09:00:00Z",
                "updatedAt",
                "2024-03-21T09:00:00Z",
                "confirmLink",
                true)),
        "client",
        Map.of(
            "firstName",
            "Alex",
            "lastName",
            "Smith",
            "dateOfBirth",
            "1990-01-01",
            "appliedPreviously",
            false,
            "correspondenceAddress",
            List.of(Map.of("location", "HOME"))),
        "proceedings",
        List.of(validProceedingV2()));
  }

  private Map<String, Object> validProceedingV2() {
    return Map.ofEntries(
        Map.entry("id", UUID.randomUUID().toString()),
        Map.entry("leadProceeding", true),
        Map.entry("ccmsCode", "SE003"),
        Map.entry("meaning", "Domestic abuse"),
        Map.entry("description", "Test proceeding"),
        Map.entry("matterType", "MATTER_TYPE"),
        Map.entry("categoryOfLaw", "FAMILY"),
        Map.entry("clientInvolvementType", "A"),
        Map.entry("usedDelegatedFunctions", false),
        Map.entry("delegatedFunctionsCostLimitation", 1000.0),
        Map.entry("substantiveCostLimitation", 2000.0),
        Map.entry("levelOfService", "FULL"),
        Map.entry(
            "scopeLimitations",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "scopeType",
                    "SCOPE",
                    "scopeLimitation",
                    "LIMITATION",
                    "scopeDescription",
                    "Scope description"))));
  }

  @Test
  void validateRejectsMissingRequiredFieldWithinCollectionItem() {
    Map<String, Object> payload =
        Map.of(
            "submittedAt",
            "2026-01-15T10:20:30Z",
            "proceedings",
            List.of(Map.of("id", UUID.randomUUID().toString(), "leadProceeding", true)));

    ValidationException ex =
        assertThrows(
            ValidationException.class,
            () -> validator.validate(payload, "ApplyApplication.json", 1));

    assertTrue(
        ex.errors().stream().anyMatch(msg -> msg.toLowerCase().contains("description")),
        "Expected validation errors to mention missing description field within proceeding. Actual errors: "
            + ex.errors());
  }
}
