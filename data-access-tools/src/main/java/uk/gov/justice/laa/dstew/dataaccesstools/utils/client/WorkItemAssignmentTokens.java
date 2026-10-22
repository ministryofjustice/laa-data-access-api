package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Builds initial assignment tokens for newly ready standalone work items. */
public final class WorkItemAssignmentTokens {
  private WorkItemAssignmentTokens() {}

  /**
   * Mirrors the service's VersionToken layout for a newly ready, unassigned standalone item only;
   * any other state must use the token read from the work list.
   */
  public static String initial(UUID itemId) {
    var raw = "v1:work-item-assignment:" + itemId + ":0";
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
