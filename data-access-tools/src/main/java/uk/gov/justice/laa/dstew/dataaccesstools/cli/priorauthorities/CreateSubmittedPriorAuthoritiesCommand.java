package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.DecisionRequestFactory;

@CommandLine.Command(
    name = "create-submitted",
    mixinStandardHelpOptions = true,
    description = "Create granted applications and submitted prior authorities.")
public final class CreateSubmittedPriorAuthoritiesCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private PriorAuthoritiesCommand priorAuthorities;

  @CommandLine.Option(
      names = "--type",
      required = true,
      description = "EXPERT, DISBURSEMENT, COUNSEL, or ALL.")
  private PriorAuthorityTypeSelector type;

  @CommandLine.Option(
      names = "--caseworker-id",
      required = true,
      description = "Existing caseworker UUID used to assign and decide each application.")
  private UUID caseworkerId;

  @CommandLine.Option(
      names = "--count",
      required = true,
      description = "Number to create per selected type.")
  private int count;

  @Override
  public Integer call() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
    var workflow =
        new PriorAuthorityCreationWorkflow(
            priorAuthorities.root().client(),
            new PriorAuthorityRequestFactory(),
            new ApplicationRequestFactory(),
            new DecisionRequestFactory());
    return priorAuthorities.root().print(workflow.createSubmitted(count, type, caseworkerId));
  }
}
