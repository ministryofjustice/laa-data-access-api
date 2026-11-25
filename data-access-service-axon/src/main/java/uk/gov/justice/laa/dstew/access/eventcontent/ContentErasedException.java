package uk.gov.justice.laa.dstew.access.eventcontent;

/**
 * Thrown when a command needs event content that has been erased and no more specific not-found
 * rule applies. Aggregate state exposes content through {@code Optional} accessors; callers use
 * {@code orElseThrow(ContentErasedException::new)}.
 */
public final class ContentErasedException extends IllegalStateException {

  public ContentErasedException() {
    super("Content has been erased");
  }
}
