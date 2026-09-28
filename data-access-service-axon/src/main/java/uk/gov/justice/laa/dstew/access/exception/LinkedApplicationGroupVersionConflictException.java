package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when a command was based on a stale linked-group version. */
@ExcludeFromGeneratedCodeCoverage
public class LinkedApplicationGroupVersionConflictException extends RuntimeException {

  /** Creates a conflict with the application's expected linked-group version. */
  public LinkedApplicationGroupVersionConflictException(
      UUID applicationId, long expectedGroupVersion) {
    this(
        "Linked group of application "
            + applicationId
            + " has changed since version "
            + expectedGroupVersion);
  }

  private LinkedApplicationGroupVersionConflictException(String message) {
    super(message);
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
}
