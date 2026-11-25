package uk.gov.justice.laa.dstew.access.eventcontent;

import org.jspecify.annotations.Nullable;

/** Test event whose {@code id} is retained and whose {@code note} is content. */
public record ContentTestEvent(@RetainedField String id, @Nullable String note) {

  public static final String TYPE = "test.Content";
}
