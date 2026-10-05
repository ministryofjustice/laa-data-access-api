package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationRequestFactoryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void sameSeedProducesIdenticalRandomFields(long seed) throws IOException {
    JsonNode first = clientOf(new ApplicationRequestFactory(seed).create());
    JsonNode second = clientOf(new ApplicationRequestFactory(seed).create());

    assertEquals(first, second);
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void differentSeedsProduceDifferentRandomFields(long seed) throws IOException {
    JsonNode first = clientOf(new ApplicationRequestFactory(seed).create());
    JsonNode second = clientOf(new ApplicationRequestFactory(seed + 1).create());

    assertNotEquals(first, second);
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void generatesStructurallyValidJson(long seed) throws IOException {
    var application = new ApplicationRequestFactory(seed).create();

    JsonNode root = MAPPER.readTree(application.request());
    JsonNode content = root.get("applicationContent");
    JsonNode client = content.get("client");
    JsonNode proceeding = content.get("proceedings").get(0);

    assertFalse(client.get("firstName").asText().isEmpty());
    assertFalse(client.get("lastName").asText().isEmpty());
    assertTrue(proceeding.get("scopeLimitations").get(0).get("code").asText().matches("FM\\d{3}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1A234B", "9Z999Y"})
  void usesSpecifiedOfficeCode(String officeCode) throws IOException {
    JsonNode provider = providerOf(new ApplicationRequestFactory(42L, officeCode).create());

    assertEquals(officeCode, provider.get("officeCode").asText());
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void generatesOfficeCodeWhenNoneIsSpecified(long seed) throws IOException {
    JsonNode provider = providerOf(new ApplicationRequestFactory(seed, null).create());

    assertTrue(provider.get("officeCode").asText().matches("[0-9][A-Z][0-9]{3}[A-Z]"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "A12345", "1a234B", "1A23B", "1A234BC"})
  void rejectsInvalidSpecifiedOfficeCode(String officeCode) {
    assertThrows(
        IllegalArgumentException.class, () -> new ApplicationRequestFactory(42L, officeCode));
  }

  private JsonNode clientOf(ApplicationRequestFactory.ApplicationData application)
      throws IOException {
    return MAPPER.readTree(application.request()).get("applicationContent").get("client");
  }

  private JsonNode providerOf(ApplicationRequestFactory.ApplicationData application)
      throws IOException {
    return MAPPER.readTree(application.request()).get("applicationContent").get("provider");
  }
}
