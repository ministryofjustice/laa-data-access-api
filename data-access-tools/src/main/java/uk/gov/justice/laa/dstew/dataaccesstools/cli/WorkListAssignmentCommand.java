package uk.gov.justice.laa.dstew.dataaccesstools.cli;

import java.util.UUID;
import picocli.CommandLine;

/** Shared API-backed assignment behaviour for a single work-list item. */
public abstract class WorkListAssignmentCommand {
  @CommandLine.Option(names = "--caseworker-id", required = true, description = "Caseworker UUID.")
  private UUID caseworkerId;

  @CommandLine.Option(
      names = "--expected-assignment-version",
      required = true,
      description = "Current assignment version from the work-list API.")
  private long expectedAssignmentVersion;

  @CommandLine.Option(
      names = "--event-description",
      description = "Optional assignment audit description.")
  private String eventDescription;

  protected final int assign(DataAccessToolsCommand root, UUID itemId) {
    if (expectedAssignmentVersion < 0) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--expected-assignment-version must not be negative");
    }
    root.client()
        .assignWorkListItem(itemId, caseworkerId, expectedAssignmentVersion, eventDescription);
    System.out.printf("%s: SUCCESS - assigned to %s%n", itemId, caseworkerId);
    return 0;
  }
}
