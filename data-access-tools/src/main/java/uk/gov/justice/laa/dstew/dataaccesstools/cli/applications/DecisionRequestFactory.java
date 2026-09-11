package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.UUID;

public final class DecisionRequestFactory {
  public String createAutograntedOutcome(ApplicationRequestFactory.ApplicationData application) {
    return """
        {"outcome":"AUTOGRANTED","certificate":{"certificateNumber":"CERT-%s"}}
        """
        .formatted(application.laaReference());
  }

  public String create(
      ApplicationRequestFactory.ApplicationData application, Decision decision, UUID caseworkerId) {
    String certificate =
        decision == Decision.GRANTED
            ? ",\"certificate\":{\"certificateNumber\":\"CERT-" + application.laaReference() + "\"}"
            : "";
    return """
      {"overallDecision":"%s","proceedings":[{"proceedingId":"%s","meritsDecision":{"decision":"%s","justification":"The application has been reviewed by a caseworker."}}],"eventHistory":{"eventDescription":"Decision created by data-access-tools"},"autoGranted":false%s,"applicationVersion":1,"caseworkerId":"%s"}
        """
        .formatted(decision, application.proceedingId(), decision, certificate, caseworkerId);
  }

  public enum Decision {
    GRANTED,
    REFUSED
  }
}
