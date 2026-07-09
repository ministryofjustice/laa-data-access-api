package uk.gov.justice.laa.dstew.access.model;

/** Interface for domain event details classes that may carry an event description. */
public interface DomainEventDetails {

  /**
   * Returns the event description associated with this domain event, or {@code null} if not
   * applicable for this event type.
   *
   * @return the event description, or {@code null}
   */
  default String getEventDescription() {
    return null;
  }
}
