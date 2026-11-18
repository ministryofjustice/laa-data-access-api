package uk.gov.justice.laa.dstew.access.eventcontent;

/** Thrown when erased event content is read. */
public final class ContentErasedException extends IllegalStateException {

  public ContentErasedException() {
    super("Content has been erased");
  }
}
