package uk.gov.justice.laa.dstew.access.integrationevent;

/** System boundary for publishing a submitted-Application trigger. */
public interface ApplicationSubmittedPublisher {

  /** Publishes the event with attributes needed by SNS subscription filters. */
  void publish(ApplicationSubmittedEvent event, String applicationType);
}
