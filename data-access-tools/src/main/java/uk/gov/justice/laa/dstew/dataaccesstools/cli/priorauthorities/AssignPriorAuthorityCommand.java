package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.WorkListAssignmentCommand;

@CommandLine.Command(
    name = "assign",
    mixinStandardHelpOptions = true,
    description = "Assign an unassigned prior authority through the work-list API.")
public final class AssignPriorAuthorityCommand extends WorkListAssignmentCommand
    implements Callable<Integer> {
  @CommandLine.ParentCommand private PriorAuthoritiesCommand priorAuthorities;

  @CommandLine.Option(
      names = "--prior-authority-id",
      required = true,
      description = "Prior-authority submission UUID.")
  private UUID priorAuthorityId;

  @Override
  public Integer call() {
    return assign(priorAuthorities.root(), priorAuthorityId);
  }
}
