package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;

/** Raised when an operation is not valid for the Application's lifecycle state. */
public class InvalidApplicationStateException extends RuntimeException {

  public InvalidApplicationStateException(UUID applicationId, String status) {
    super("Application " + applicationId + " cannot be made ready from status " + status);
  }
}
