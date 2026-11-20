package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.ApplicationDecisionData;

class DecisionRequestFactoryTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final DecisionRequestFactory factory = new DecisionRequestFactory();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenGeneratedApplication_whenGranted_thenCertificateContainsItsDetails(boolean autoGrant)
      throws Exception {
    var application = new ApplicationRequestFactory(42, "1A234B", 2, null).create();
    JsonNode content = JSON.readTree(application.request()).required("applicationContent");

    JsonNode request =
        JSON.readTree(
            autoGrant
                ? factory.createAutograntedOutcome(application)
                : factory.create(application, DecisionRequestFactory.Decision.GRANTED));

    JsonNode certificate = request.required("certificate");
    assertEquals(
        application.applicationId().toString(), certificate.path("applicationId").asText());
    assertEquals(application.laaReference(), certificate.path("laaReference").asText());
    assertEquals(content.required("opponents"), certificate.required("opponents"));
    JsonNode client = certificate.required("client");
    assertEquals(content.at("/client/firstName"), client.required("firstName"));
    assertEquals(content.at("/client/lastName"), client.required("lastName"));
    assertEquals(content.at("/client/addresses/0"), client.required("homeAddress"));
    assertTrue(client.required("correspondenceAddress").isNull());
    assertEquals("Test provider 1A234B", certificate.at("/provider/firmName").asText());
    assertFalse(certificate.at("/provider/officeAddress/addressLineOne").asText().isBlank());

    JsonNode proceeding = content.required("proceedings").get(0);
    JsonNode certified = certificate.required("proceedings").get(0);
    assertEquals(application.proceedingId().toString(), certified.path("proceedingId").asText());
    assertEquals(proceeding.required("leadProceeding"), certified.required("isLead"));
    assertEquals(proceeding.required("meaning"), certified.required("proceedingType"));
    assertEquals(proceeding.required("description"), certified.required("proceedingDescription"));
    assertEquals(proceeding.required("matterType"), certified.required("matterType"));
    assertEquals(proceeding.required("categoryOfLaw"), certified.required("categoryOfLaw"));
    assertEquals(
        proceeding.required("substantiveLevelOfServiceName"), certified.required("levelOfService"));
    assertEquals("GRANTED", certified.required("meritsDecision").asText());
    assertEquals(
        proceeding.at("/scopeLimitations/0/meaning"),
        certified.at("/scopeLimitations/0/scopeLimitation"));
    assertEquals(
        proceeding.at("/scopeLimitations/0/description"),
        certified.at("/scopeLimitations/0/scopeDescription"));

    JsonNode summary = certificate.required("certificateSummary");
    assertEquals(
        proceeding.required("substantiveCostLimitation").asInt(),
        summary.required("substantiveCostLimitation").asInt());
    UUID.fromString(summary.required("certificateId").asText());
    LocalDate issued =
        OffsetDateTime.parse(summary.required("certificateIssueDate").asText()).toLocalDate();
    assertEquals(issued.toString(), summary.required("startDate").asText());
    assertEquals(issued.toString(), summary.required("costLimitEffectiveDate").asText());
    assertEquals(issued.plusMonths(6).toString(), summary.required("endDate").asText());
    assertEquals(summary.required("endDate"), summary.required("certificateLimitationDate"));
    assertEquals(summary.required("startDate"), certified.required("limitationEffectiveDate"));
    assertEquals(summary.required("startDate"), certified.required("levelOfServiceEffectiveDate"));
    assertEquals(summary.required("endDate"), certified.required("limitationEndDate"));
  }

  @Test
  void givenCurrentApplication_whenGranted_thenAllProceedingsAndCorrespondenceArePreserved()
      throws Exception {
    ObjectNode details = details();
    ObjectNode first = (ObjectNode) details.required("proceedings").get(0);
    first.put("substantiveCostLimitation", 1234.99);
    first.put("delegatedFunctionsDate", "2026-01-02");
    ObjectNode second =
        first
            .deepCopy()
            .put("proceedingId", UUID.randomUUID().toString())
            .put("leadProceeding", false)
            .put("substantiveCostLimitation", 2000);
    ObjectNode third =
        first
            .deepCopy()
            .put("proceedingId", UUID.randomUUID().toString())
            .putNull("substantiveCostLimitation");
    details.withArray("proceedings").add(second).add(third);
    ObjectNode client = (ObjectNode) details.required("client");
    client.put("firstName", "Anne \"Annie\"");
    var correspondence =
        client
            .withArray("addresses")
            .addObject()
            .put("location", "correspondence")
            .put("addressLineOne", "2 Correspondence Road")
            .put("careOf", "ORGANISATION")
            .put("careOfOrganisationName", "Solicitor & Co");

    JsonNode request =
        JSON.readTree(
            factory.create(decisionData(details), DecisionRequestFactory.Decision.GRANTED));
    JsonNode certificate = request.required("certificate");

    assertEquals(7, request.required("applicationVersion").asLong());
    assertEquals("Anne \"Annie\"", certificate.at("/client/firstName").asText());
    assertEquals(correspondence, certificate.at("/client/correspondenceAddress"));
    assertEquals(3234, certificate.at("/certificateSummary/substantiveCostLimitation").asInt());
    assertEquals(3, certificate.required("proceedings").size());
    assertEquals(second.required("proceedingId"), certificate.at("/proceedings/1/proceedingId"));
    assertEquals(third.required("proceedingId"), certificate.at("/proceedings/2/proceedingId"));
    assertEquals("2026-01-02", certificate.at("/proceedings/0/delegatedFunctionsDate").asText());
    assertFalse(certificate.at("/proceedings/1/isLead").asBoolean());
    for (JsonNode proceeding : certificate.required("proceedings")) {
      assertEquals("GRANTED", proceeding.required("meritsDecision").asText());
    }
    JsonNode next =
        JSON.readTree(
            factory.create(decisionData(details), DecisionRequestFactory.Decision.GRANTED));
    assertNotEquals(
        certificate.at("/certificateSummary/certificateId"),
        next.at("/certificate/certificateSummary/certificateId"));
  }

  @Test
  void givenRefusedApplication_whenMakingDecision_thenNoCertificateIsIncluded() throws Exception {
    var data = new ApplicationDecisionData("L-123-456", List.of(UUID.randomUUID()), 7, null);
    JsonNode request = JSON.readTree(factory.create(data, DecisionRequestFactory.Decision.REFUSED));
    assertFalse(request.has("certificate"));
    assertEquals("REFUSED", request.at("/proceedings/0/meritsDecision/decision").asText());
  }

  @Test
  void givenMissingClientAddress_whenGranted_thenFailsRatherThanSendingUnrenderableCertificate()
      throws Exception {
    ObjectNode details = details();
    ((ObjectNode) details.required("client")).putArray("addresses");
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create(decisionData(details), DecisionRequestFactory.Decision.GRANTED));
  }

  private ObjectNode details() throws Exception {
    var application = new ApplicationRequestFactory(42).create();
    ObjectNode details =
        JSON.readTree(application.request()).required("applicationContent").deepCopy();
    details.put("applicationId", application.applicationId().toString());
    details.put("laaReference", application.laaReference());
    ((ObjectNode) details.required("proceedings").get(0))
        .put("proceedingId", application.proceedingId().toString())
        .put("meritsDecision", "PENDING");
    return details;
  }

  private ApplicationDecisionData decisionData(JsonNode details) {
    return new ApplicationDecisionData(
        details.required("laaReference").asText(),
        details
            .required("proceedings")
            .valueStream()
            .map(proceeding -> UUID.fromString(proceeding.required("proceedingId").asText()))
            .toList(),
        7,
        details);
  }
}
