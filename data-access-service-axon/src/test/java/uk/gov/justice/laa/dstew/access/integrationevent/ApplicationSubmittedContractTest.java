package uk.gov.justice.laa.dstew.access.integrationevent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

class ApplicationSubmittedContractTest {

  private final JsonSchemaValidator validator = new JsonSchemaValidator();

  @Test
  void givenOptionalDataFieldAdded_whenVersionOneValidated_thenContractRemainsCompatible() {
    Map<String, Object> data = new HashMap<>();
    data.put("applicationId", UUID.randomUUID().toString());
    data.put("applyApplicationId", UUID.randomUUID().toString());
    data.put("laaReference", null);
    data.put("applicationVersion", 3);
    data.put("futureOptionalField", "ignored-by-v1-consumers");
    Map<String, Object> envelope =
        Map.of(
            "eventType", "ApplicationSubmitted",
            "schemaVersion", 1,
            "eventId", UUID.randomUUID().toString(),
            "occurredAt", "2026-08-03T09:30:00Z",
            "source", "laa-data-access-api",
            "correlationId", "corr-2096",
            "data", data);

    validator.validate(envelope, "ApplicationSubmitted.json", 1);
  }
}
