package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;

/** Raised when manual readiness would overwrite an existing automatic-grant outcome. */
public class ApplicationAutoGrantOutcomeConflictException extends RuntimeException {

  public ApplicationAutoGrantOutcomeConflictException(UUID applicationId) {
    super("Application " + applicationId + " already has an incompatible auto-grant outcome");
  }
}
