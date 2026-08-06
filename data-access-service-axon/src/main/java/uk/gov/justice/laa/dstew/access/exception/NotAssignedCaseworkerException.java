package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;

public class NotAssignedCaseworkerException extends RuntimeException {
  public NotAssignedCaseworkerException(UUID applicationId) {
    super("The caseworker is not assigned to application: " + applicationId);
  }
}
