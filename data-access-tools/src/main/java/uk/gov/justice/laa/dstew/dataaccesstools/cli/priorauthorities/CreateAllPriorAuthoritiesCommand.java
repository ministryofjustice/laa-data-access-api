package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "create-all",
    description = "Create expert, disbursement, and counsel prior authorities.")
public final class CreateAllPriorAuthoritiesCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private PriorAuthoritiesCommand priorAuthorities;

  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Application UUID.")
  private UUID applicationId;

  @Override
  public Integer call() {
    var workflow =
        new PriorAuthorityCreationWorkflow(
            priorAuthorities.root().client(), new PriorAuthorityRequestFactory());
    return priorAuthorities.root().print(workflow.createAll(applicationId));
  }
}
