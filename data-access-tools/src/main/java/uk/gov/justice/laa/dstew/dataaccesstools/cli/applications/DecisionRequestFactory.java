package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

public final class DecisionRequestFactory {
  public String create(ApplicationRequestFactory.ApplicationData application, Decision decision) {
    String certificate =
        decision == Decision.GRANTED
            ? ",\"certificate\":{\"certificateNumber\":\"CERT-" + application.laaReference() + "\"}"
            : "";
    return """
        {"overallDecision":"%s","proceedings":[{"proceedingId":"%s","meritsDecision":{"decision":"%s","justification":"The application has been reviewed by a caseworker."}}],"eventHistory":{"eventDescription":"Decision created by data-access-tools"},"autoGranted":false%s,"applicationVersion":1}
        """
        .formatted(decision, application.proceedingId(), decision, certificate);
  }

  public enum Decision {
    GRANTED,
    REFUSED
  }
}
