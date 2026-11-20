package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Builds Decide's CertificateV1 payload without requiring provider-directory access. */
final class CertificateRequestFactory {
  private static final ObjectMapper JSON = new ObjectMapper();

  ObjectNode create(JsonNode application) {
    OffsetDateTime issuedAt = OffsetDateTime.now();
    LocalDate today = issuedAt.toLocalDate();
    String start = today.toString();
    String end = today.plusMonths(6).toString();
    var certificate =
        JSON.createObjectNode()
            .put("schemaMajorVersion", 1)
            .put("schemaMinorVersion", 0)
            .put("certificateVersion", 1);
    certificate.set("applicationId", application.required("applicationId"));
    certificate.set("laaReference", application.required("laaReference"));
    certificate.set("opponents", application.required("opponents").deepCopy());
    certificate
        .putObject("guardian")
        .putNull("firstName")
        .putNull("lastName")
        .putNull("correspondenceAddress");
    certificate.set("client", client(application.required("client")));
    certificate.set("provider", testProvider(application.required("provider")));

    int totalCost = 0;
    var proceedings = certificate.putArray("proceedings");
    for (JsonNode proceeding : application.required("proceedings")) {
      JsonNode cost = proceeding.get("substantiveCostLimitation");
      if (cost != null && !cost.isNull()) {
        totalCost += new BigDecimal(cost.asText()).intValue();
      }
      var entry =
          proceedings
              .addObject()
              .put("meritsDecision", "GRANTED")
              .put("certificateType", "SUBSTANTIVE")
              .put("proceedingStatus", "LIVE")
              .put("limitationEffectiveDate", start)
              .put("levelOfServiceEffectiveDate", start)
              .put("previousLevelOfServiceEffectiveDate", start)
              .put("limitationEndDate", end)
              .put("proceedingEndDate", end);
      entry.set("proceedingId", proceeding.required("proceedingId"));
      entry.set("isLead", proceeding.required("leadProceeding"));
      entry.set("matterType", proceeding.required("matterType"));
      entry.set("categoryOfLaw", proceeding.required("categoryOfLaw"));
      entry.set("clientInvolvementType", proceeding.required("clientInvolvementType"));
      entry.set("proceedingType", proceeding.required("meaning"));
      entry.set("proceedingDescription", proceeding.required("description"));
      entry.set("levelOfService", proceeding.required("substantiveLevelOfServiceName"));
      entry.set("previousLevelOfService", proceeding.required("substantiveLevelOfServiceName"));
      entry.set("delegatedFunctionsDate", proceeding.get("delegatedFunctionsDate"));
      var scopes = entry.putArray("scopeLimitations");
      for (JsonNode scope : proceeding.required("scopeLimitations")) {
        var limitation = scopes.addObject();
        limitation.set("scopeLimitation", scope.required("meaning"));
        limitation.set("scopeDescription", scope.required("description"));
      }
    }

    certificate
        .putObject("certificateSummary")
        .put("certificateId", UUID.randomUUID().toString())
        .put("certificateIssueDate", issuedAt.toString())
        .put("certificateType", "SUBSTANTIVE")
        .put("certificateStatusCode", "LIVE")
        .put("startDate", start)
        .put("endDate", end)
        .put("certificateLimitationDate", end)
        .put("costLimitEffectiveDate", start)
        .put("substantiveCostLimitation", totalCost)
        .putNull("reinstatementDate");
    return certificate;
  }

  private ObjectNode client(JsonNode applicationClient) {
    var client = JSON.createObjectNode();
    client.set("firstName", applicationClient.required("firstName"));
    client.set("lastName", applicationClient.required("lastName"));
    client.putNull("correspondenceAddress");
    client.putNull("homeAddress");
    for (JsonNode address : applicationClient.required("addresses")) {
      String location = address.required("location").asText();
      if ("correspondence".equalsIgnoreCase(location)
          && client.path("correspondenceAddress").isNull()) {
        client.set("correspondenceAddress", address.deepCopy());
      } else if ("home".equalsIgnoreCase(location) && client.path("homeAddress").isNull()) {
        client.set("homeAddress", address.deepCopy());
      }
    }
    if (client.path("correspondenceAddress").isNull() && client.path("homeAddress").isNull()) {
      throw new IllegalArgumentException(
          "Certificate requires a client home or correspondence address");
    }
    return client;
  }

  private ObjectNode testProvider(JsonNode provider) {
    String firmName = "Test provider " + provider.required("officeCode").asText();
    var details = JSON.createObjectNode().put("firmName", firmName);
    details
        .putObject("officeAddress")
        .put("careOfOrganisationName", firmName)
        .put("addressLineOne", "1 Test Street")
        .put("city", "London")
        .put("postcode", "SW1A 1AA")
        .put("countryCode", "GBR")
        .put("countryName", "United Kingdom");
    return details;
  }
}
