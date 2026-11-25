package uk.gov.justice.laa.dstew.access.eventcontent;

/**
 * Thrown when an event whose type has content fields has no content row. Erasure keeps the row, so
 * a missing one means the content was lost, not erased.
 */
public final class MissingEventContentException extends IllegalStateException {

  public MissingEventContentException(String typeName, String eventId) {
    super("Event " + eventId + " of type " + typeName + " has no content row");
  }
}
