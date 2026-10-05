package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationRequestFactoryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 123456789L})
  void generatesValidUniqueReferences(long seed) throws IOException {
    var factory = new ApplicationRequestFactory(seed);
    Set<String> references = new HashSet<>();
    Set<Character> characters = new HashSet<>();
    for (int index = 0; index < 100; index++) {
      var application = factory.create();
      String reference = application.laaReference();
      assertTrue(reference.matches("L-[0-9ABCDEFHJKLMNPRTUVWXY]{3}-[0-9ABCDEFHJKLMNPRTUVWXY]{3}"));
      assertTrue(references.add(reference));
      assertEquals(reference, MAPPER.readTree(application.request()).get("laaReference").asText());
      reference.substring(2).chars().forEach(character -> characters.add((char) character));
    }
    assertTrue(characters.contains('U'));
    assertTrue(characters.contains('0'));
  }

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
  @ValueSource(longs = {1L, 42L, 123456789L})
  void generatesCompleteDraftReadyForSubmission(long seed) throws IOException {
    var application = new ApplicationRequestFactory(seed).create();
    JsonNode request = MAPPER.readTree(application.request());
    JsonNode content = request.required("applicationContent");
    assertEquals(application.applicationId().toString(), request.required("id").asText());
    assertEquals(application.laaReference(), request.required("laaReference").asText());
    assertEquals("APPLICATION_SUBMITTED", request.required("status").asText());
    Instant.parse(content.required("createdAt").asText());
    Instant.parse(content.required("submittedAt").asText());
    assertFalse(content.required("provider").required("officeCode").asText().isBlank());
    assertFalse(content.required("provider").required("contactEmail").asText().isBlank());
    JsonNode client = content.required("client");
    LocalDate.parse(client.required("dateOfBirth").asText());
    assertEquals(1, client.required("addresses").size());
    assertFalse(client.required("addresses").get(0).required("postcode").asText().isBlank());
    JsonNode proceedings = content.required("proceedings");
    assertEquals(1, proceedings.size());
    assertEquals(application.proceedingId().toString(), proceedings.get(0).required("id").asText());
    assertTrue(proceedings.get(0).required("leadProceeding").asBoolean());
    assertFalse(proceedings.get(0).required("scopeLimitations").isEmpty());
  }

  private JsonNode clientOf(ApplicationRequestFactory.ApplicationData application)
      throws IOException {
    return MAPPER.readTree(application.request()).get("applicationContent").get("client");
  }
}
