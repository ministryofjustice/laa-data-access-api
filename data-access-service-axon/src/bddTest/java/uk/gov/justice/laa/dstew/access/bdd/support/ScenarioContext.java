package uk.gov.justice.laa.dstew.access.bdd.support;

import java.util.UUID;

public final class ScenarioContext {
  private UUID applicationId;
  private UUID proceedingId;
  private UUID priorAuthorityId;
  private UUID workItemId;
  private long applicationVersion;
  private long priorAuthorityVersion;
  private long assignmentVersion;
  private ApiResponse lastResponse;

  public UUID applicationId() {
    return required(applicationId, "application ID");
  }

  public void applicationId(UUID value) {
    applicationId = value;
  }

  public UUID proceedingId() {
    return required(proceedingId, "proceeding ID");
  }

  public void proceedingId(UUID value) {
    proceedingId = value;
  }

  public UUID priorAuthorityId() {
    return required(priorAuthorityId, "prior authority ID");
  }

  public void priorAuthorityId(UUID value) {
    priorAuthorityId = value;
  }

  public UUID workItemId() {
    return required(workItemId, "work-list item ID");
  }

  public void workItemId(UUID value) {
    workItemId = value;
  }

  public long applicationVersion() {
    return applicationVersion;
  }

  public void applicationVersion(long value) {
    applicationVersion = value;
  }

  public long priorAuthorityVersion() {
    return priorAuthorityVersion;
  }

  public void priorAuthorityVersion(long value) {
    priorAuthorityVersion = value;
  }

  public long assignmentVersion() {
    return assignmentVersion;
  }

  public void assignmentVersion(long value) {
    assignmentVersion = value;
  }

  public ApiResponse lastResponse() {
    return lastResponse;
  }

  public void lastResponse(ApiResponse value) {
    lastResponse = value;
  }

  private static UUID required(UUID value, String name) {
    if (value == null)
      throw new IllegalStateException("No " + name + " is available in this scenario");
    return value;
  }
}
