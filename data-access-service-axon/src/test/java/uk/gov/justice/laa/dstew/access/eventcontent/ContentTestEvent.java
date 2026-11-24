package uk.gov.justice.laa.dstew.access.eventcontent;

/** Test event whose {@code id} is retained and whose {@code note} is content. */
public record ContentTestEvent(@RetainedField String id, Erasable<String> note) {

  public static final String TYPE = "test.Content";

  public ContentTestEvent {
    note = Erasable.orErased(note);
  }
}
