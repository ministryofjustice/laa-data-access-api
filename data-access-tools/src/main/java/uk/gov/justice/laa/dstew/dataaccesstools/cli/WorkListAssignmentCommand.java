package uk.gov.justice.laa.dstew.dataaccesstools.cli;

import java.util.UUID;
import picocli.CommandLine;

/** Shared API-backed assignment behaviour for a single work-list item. */
public abstract class WorkListAssignmentCommand {
  @CommandLine.Option(
      names = "--expected-assignment-version",
      required = true,
      description =
          "Current assignmentVersion token from the work-list API, passed back unchanged.")
  private String expectedAssignmentVersion;

  @CommandLine.Option(
      names = "--event-description",
      description = "Optional assignment audit description.")
  private String eventDescription;

  protected final int assign(DataAccessToolsCommand root, UUID itemId) {
    if (expectedAssignmentVersion == null || expectedAssignmentVersion.isBlank()) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--expected-assignment-version must not be blank");
    }
    root.client().assignWorkListItem(itemId, expectedAssignmentVersion, eventDescription);
    System.out.printf("%s: SUCCESS - assigned to authenticated user%n", itemId);
    return 0;
  }
}
