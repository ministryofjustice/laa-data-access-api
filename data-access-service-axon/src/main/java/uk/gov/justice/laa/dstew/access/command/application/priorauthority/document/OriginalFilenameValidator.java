package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

/** Validates uploaded filename metadata before it is persisted or reflected in a response. */
final class OriginalFilenameValidator {

  static final int MAX_LENGTH = 255;

  private OriginalFilenameValidator() {}

  static String validate(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalArgumentException("originalFilename must not be blank");
    }
    if (originalFilename.codePointCount(0, originalFilename.length()) > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "originalFilename must not exceed %d characters".formatted(MAX_LENGTH));
    }
    if (originalFilename.indexOf('/') >= 0 || originalFilename.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("originalFilename must not contain path separators");
    }
    if (originalFilename.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("originalFilename must not contain control characters");
    }

    int extensionIndex = originalFilename.lastIndexOf('.');
    if (extensionIndex <= 0 || extensionIndex == originalFilename.length() - 1) {
      throw new IllegalArgumentException("originalFilename must include a file extension");
    }
    return originalFilename;
  }
}
