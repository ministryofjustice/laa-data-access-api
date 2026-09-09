package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.WorkListAssignmentCommand;

@CommandLine.Command(
    name = "assign",
    mixinStandardHelpOptions = true,
    description = "Assign an unassigned application through the work-list API.")
public final class AssignApplicationCommand extends WorkListAssignmentCommand
    implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Application UUID.")
  private UUID applicationId;

  @Override
  public Integer call() {
    return assign(applications.root(), applicationId);
  }
}
