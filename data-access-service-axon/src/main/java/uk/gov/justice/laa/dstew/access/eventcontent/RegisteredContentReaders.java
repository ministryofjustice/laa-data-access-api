package uk.gov.justice.laa.dstew.access.eventcontent;

import java.util.Set;

/**
 * The reviewed list of event processors that receive event content. Every other processor receives
 * content erased. Each processor added here must declare what content it stores and how erasure
 * reaches it.
 */
public final class RegisteredContentReaders {

  /** Builds the application details read model, including potential duplicates and documents. */
  public static final String APPLICATION_PROJECTION = "application-projection";

  /** Builds the prior authority read model, including documents and decision details. */
  public static final String PRIOR_AUTHORITY_PROJECTION = "prior-authority-projection";

  public static final Set<String> NAMES =
      Set.of(APPLICATION_PROJECTION, PRIOR_AUTHORITY_PROJECTION);

  private RegisteredContentReaders() {}

  /**
   * Checks that a processor may read content.
   *
   * @throws IllegalStateException if it is not on the list
   */
  public static void require(String processorName) {
    if (!NAMES.contains(processorName)) {
      throw new IllegalStateException(processorName + " is not a registered content reader");
    }
  }
}
