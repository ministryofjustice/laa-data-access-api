package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.DecisionRequestFactory;

@CommandLine.Command(
    name = "create-drafts",
    mixinStandardHelpOptions = true,
    description = "Create prior-authority drafts for a granted application.")
public final class CreatePriorAuthorityDraftsCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private PriorAuthoritiesCommand priorAuthorities;

  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Granted application UUID.")
  private UUID applicationId;

  @CommandLine.Option(
      names = "--type",
      required = true,
      description = "EXPERT, DISBURSEMENT, COUNSEL, or ALL.")
  private PriorAuthorityTypeSelector type;

  @CommandLine.Option(
      names = "--count",
      required = true,
      description = "Number to create per selected type.")
  private int count;

  @Override
  public Integer call() {
    validateCount();
    return priorAuthorities.root().print(workflow().createDrafts(applicationId, count, type));
  }

  private PriorAuthorityCreationWorkflow workflow() {
    return new PriorAuthorityCreationWorkflow(
        priorAuthorities.root().client(),
        new PriorAuthorityRequestFactory(),
        new ApplicationRequestFactory(),
        new DecisionRequestFactory());
  }

  private void validateCount() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
  }
}
