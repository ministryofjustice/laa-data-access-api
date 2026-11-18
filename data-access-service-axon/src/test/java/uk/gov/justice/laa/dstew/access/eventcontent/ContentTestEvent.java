package uk.gov.justice.laa.dstew.access.eventcontent;

/** Test event classified by {@code event-schemas/test.Content.json}: {@code id} is kept. */
public record ContentTestEvent(String id, Erasable<String> note) {

  public static final String TYPE = "test.Content";

  public ContentTestEvent {
    note = Erasable.orErased(note);
  }
}
