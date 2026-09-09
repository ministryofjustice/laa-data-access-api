package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.ApplicationDecisionData;

public final class DecisionRequestFactory {
  public String createAutograntedOutcome(ApplicationRequestFactory.ApplicationData application) {
    return """
        {"outcome":"AUTOGRANTED","certificate":{"certificateNumber":"CERT-%s"}}
        """
        .formatted(application.laaReference());
  }

  public String create(ApplicationRequestFactory.ApplicationData application, Decision decision) {
    return create(application.proceedingId(), application.laaReference(), decision);
  }

  public String create(UUID proceedingId, String certificateReference, Decision decision) {
    return create(List.of(proceedingId), certificateReference, 1, decision);
  }

  public String create(ApplicationDecisionData application, Decision decision) {
    return create(
        application.proceedingIds(),
        application.laaReference(),
        application.applicationVersion(),
        decision);
  }

  private String create(
      List<UUID> proceedingIds,
      String certificateReference,
      long applicationVersion,
      Decision decision) {
    String certificate =
        decision == Decision.GRANTED
            ? ",\"certificate\":{\"certificateNumber\":\"CERT-" + certificateReference + "\"}"
            : "";
    String proceedings =
        proceedingIds.stream()
            .map(
                proceedingId ->
                    "{\"proceedingId\":\"%s\",\"meritsDecision\":{\"decision\":\"%s\",\"justification\":\"The application has been reviewed by a caseworker.\"}}"
                        .formatted(proceedingId, decision))
            .collect(java.util.stream.Collectors.joining(","));
    return """
        {"overallDecision":"%s","proceedings":[%s],"eventHistory":{"eventDescription":"Decision created by data-access-tools"},"autoGranted":false%s,"applicationVersion":%d,"caseworkerId":"8a082fe2-d539-4177-aae3-7498fd5904c7"}
        """
        .formatted(decision, proceedings, certificate, applicationVersion);
  }

  public enum Decision {
    GRANTED,
    REFUSED
  }
}
