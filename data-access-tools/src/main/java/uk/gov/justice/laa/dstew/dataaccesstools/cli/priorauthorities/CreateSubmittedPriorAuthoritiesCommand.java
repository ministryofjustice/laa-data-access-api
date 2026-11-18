package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.DecisionRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.OfficeCodeConverter;

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
      names = "--count",
      required = true,
      description = "Number to create per selected type.")
  private int count;

  @CommandLine.Option(
      names = "--office-code",
      converter = OfficeCodeConverter.class,
      description = "Provider office code to use for every created application.")
  private String officeCode;

  @Override
  public Integer call() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
    var workflow =
        new PriorAuthorityCreationWorkflow(
            priorAuthorities.root().client(),
            new PriorAuthorityRequestFactory(),
            new ApplicationRequestFactory(priorAuthorities.root().seed(), officeCode),
            new DecisionRequestFactory());
    return priorAuthorities.root().print(workflow.createSubmitted(count, type));
  }
}
