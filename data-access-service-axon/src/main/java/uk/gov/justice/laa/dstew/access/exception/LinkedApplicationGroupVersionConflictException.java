package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when a command was based on a stale linked-group version. */
@ExcludeFromGeneratedCodeCoverage
public class LinkedApplicationGroupVersionConflictException extends RuntimeException {

  /** Creates a conflict when the supplied token's version does not match the group. */
  public LinkedApplicationGroupVersionConflictException(UUID applicationId) {
    this(
        "Linked group of application "
            + applicationId
            + " does not match the supplied linkedGroupVersion; re-read before retrying");
  }

  private LinkedApplicationGroupVersionConflictException(String message) {
    super(message);
  }

  /** Creates a conflict when a standalone application tries to join a group at a stale version. */
  public static LinkedApplicationGroupVersionConflictException groupVersionMismatch() {
    return new LinkedApplicationGroupVersionConflictException(
        "Target linked group's version does not match the supplied linkedGroupVersion;"
            + " re-read before retrying");
  }

  /** Creates a conflict when the target is no longer linked to a group. */
  public static LinkedApplicationGroupVersionConflictException targetNoLongerLinked(
      UUID targetApplicationId) {
    return new LinkedApplicationGroupVersionConflictException(
        "Application "
            + targetApplicationId
            + " is no longer in a linked group; re-read before linking");
  }

  /** Creates a conflict when the target's linked-group version was not supplied. */
  public static LinkedApplicationGroupVersionConflictException versionRequired(
      UUID targetApplicationId) {
    return new LinkedApplicationGroupVersionConflictException(
        "Application "
            + targetApplicationId
            + " is in a linked group; linkedGroupVersion is required");
  }

  /** Creates a conflict when the application is no longer in the group the caller last read. */
  public static LinkedApplicationGroupVersionConflictException groupChanged(UUID applicationId) {
    return new LinkedApplicationGroupVersionConflictException(
        "Application "
            + applicationId
            + " is no longer in the linked group identified by linkedGroupVersion;"
            + " re-read before retrying");
  }
}
