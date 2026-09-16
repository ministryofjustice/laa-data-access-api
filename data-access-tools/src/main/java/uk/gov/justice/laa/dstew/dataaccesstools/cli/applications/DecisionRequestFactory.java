package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.ApplicationDecisionData;

public final class DecisionRequestFactory {
  public String createAutograntedOutcome(ApplicationRequestFactory.ApplicationData application) {
    return """
        {"outcome":"AUTOGRANTED","certificate":{"certificateNumber":"CERT-%s"}}
        """
        .formatted(application.laaReference());
  }

  public String create(
      ApplicationRequestFactory.ApplicationData application, Decision decision, UUID caseworkerId) {
    return create(
        application.laaReference(),
        java.util.List.of(application.proceedingId()),
        1,
        decision,
        caseworkerId);
  }

  public String create(ApplicationDecisionData application, Decision decision, UUID caseworkerId) {
    return create(
        application.laaReference(),
        application.proceedingIds(),
        application.applicationVersion(),
        decision,
        caseworkerId);
  }

  private String create(
      String laaReference,
      java.util.List<UUID> proceedingIds,
      long applicationVersion,
      Decision decision,
      UUID caseworkerId) {
    String certificate =
        decision == Decision.GRANTED
            ? ",\"certificate\":{\"certificateNumber\":\"CERT-" + laaReference + "\"}"
            : "";
    String proceedings =
        proceedingIds.stream()
            .map(
                proceedingId ->
                    "{\"proceedingId\":\"%s\",\"meritsDecision\":{\"decision\":\"%s\",\"justification\":\"The application has been reviewed by a caseworker.\"}}"
                        .formatted(proceedingId, decision))
            .collect(Collectors.joining(","));
    return """
      {"overallDecision":"%s","proceedings":[%s],"eventHistory":{"eventDescription":"Decision created by data-access-tools"},"autoGranted":false%s,"applicationVersion":%d,"caseworkerId":"%s"}
        """
        .formatted(decision, proceedings, certificate, applicationVersion, caseworkerId);
  }

  public enum Decision {
    GRANTED,
    REFUSED
  }
}
