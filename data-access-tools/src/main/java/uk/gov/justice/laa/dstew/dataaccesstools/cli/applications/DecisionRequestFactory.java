package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.ApplicationDecisionData;

public final class DecisionRequestFactory {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final CertificateRequestFactory certificateFactory = new CertificateRequestFactory();

  public String createAutograntedOutcome(ApplicationRequestFactory.ApplicationData application) {
    var request = JSON.createObjectNode().put("outcome", "AUTOGRANTED");
    request.set("certificate", certificateFactory.create(applicationDetails(application)));
    return request.toString();
  }

  public String create(ApplicationRequestFactory.ApplicationData application, Decision decision) {
    return create(
        new ApplicationDecisionData(
            application.laaReference(),
            java.util.List.of(application.proceedingId()),
            1,
            applicationDetails(application)),
        decision);
  }

  public String create(ApplicationDecisionData application, Decision decision) {
    var request =
        JSON.createObjectNode()
            .put("overallDecision", decision.name())
            .put("autoGranted", false)
            .put("applicationVersion", application.applicationVersion());
    request
        .putObject("eventHistory")
        .put("eventDescription", "Decision created by data-access-tools");
    var proceedings = request.putArray("proceedings");
    for (var proceedingId : application.proceedingIds()) {
      proceedings
          .addObject()
          .put("proceedingId", proceedingId.toString())
          .putObject("meritsDecision")
          .put("decision", decision.name())
          .put("justification", "The application has been reviewed by a caseworker.");
    }
    if (decision == Decision.GRANTED) {
      request.set("certificate", certificateFactory.create(application.applicationDetails()));
    }
    return request.toString();
  }

  private ObjectNode applicationDetails(ApplicationRequestFactory.ApplicationData application) {
    try {
      var request = JSON.readTree(application.request());
      ObjectNode details = request.required("applicationContent").deepCopy();
      details.put("applicationId", application.applicationId().toString());
      details.put("laaReference", application.laaReference());
      for (var proceeding : details.required("proceedings")) {
        if (!(proceeding instanceof ObjectNode object)) {
          throw new IllegalArgumentException("Application proceeding must be an object");
        }
        object.set("proceedingId", object.required("id"));
      }
      return details;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Cannot read generated application request", exception);
    }
  }

  public enum Decision {
    GRANTED,
    REFUSED
  }
}
