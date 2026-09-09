package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "create-autogranted",
    mixinStandardHelpOptions = true,
    description = "Create applications that are autogranted.")
public final class CreateAutograntedApplicationsCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--count",
      required = true,
      description = "Number of applications to create.")
  private int count;

  @Override
  public Integer call() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
    var workflow =
        new ApplicationCreationWorkflow(
            applications.root().client(),
            new ApplicationRequestFactory(),
            new DecisionRequestFactory());
    return applications.root().print(workflow.createAutogranted(count));
  }
}
