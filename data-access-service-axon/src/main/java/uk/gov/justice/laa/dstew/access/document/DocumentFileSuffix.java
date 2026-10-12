package uk.gov.justice.laa.dstew.access.document;

/** Extracts the exact storage suffix without retaining the original filename. */
public final class DocumentFileSuffix {

  private DocumentFileSuffix() {}

  /** Returns the case-sensitive suffix, including its dot, or an empty string if absent. */
  public static String fromFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return "";
    }
    int separator = filename.lastIndexOf('.');
    return separator < 0 ? "" : filename.substring(separator);
  }
}
