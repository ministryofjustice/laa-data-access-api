package uk.gov.justice.laa.dstew.dataaccesstools.cli;

import java.net.URI;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationsCommand;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities.PriorAuthoritiesCommand;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.HttpDataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

@CommandLine.Command(
    name = "data-access-tools",
    mixinStandardHelpOptions = true,
    description = "Create realistic Data Access API data.",
    subcommands = {ApplicationsCommand.class, PriorAuthoritiesCommand.class})
public final class DataAccessToolsCommand implements Callable<Integer> {
  @CommandLine.Option(
      names = "--api-url",
      required = true,
      description = "Base URL of the Data Access API.")
  private URI apiUrl;

  public static void main(String[] arguments) {
    System.exit(new CommandLine(new DataAccessToolsCommand()).execute(arguments));
  }

  public HttpDataAccessApiClient client() {
    if (!"http".equalsIgnoreCase(apiUrl.getScheme())
        && !"https".equalsIgnoreCase(apiUrl.getScheme())) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--api-url must use http or https");
    }
    return new HttpDataAccessApiClient(apiUrl);
  }

  public int print(WorkflowResult result) {
    result.items().forEach(DataAccessToolsCommand::getPrintf);
    return result.succeeded() ? 0 : 1;
  }

  private static void getPrintf(WorkflowResult.ItemResult item) {
    if (item.submissionId() == null) {
      System.out.printf(
          "%s: %s - %s%n",
          item.identifier(), item.succeeded() ? "SUCCESS" : "FAILED", item.detail());
      return;
    }
    System.out.printf(
        "%s: %s - %s -priorAuthorityID: %s%n",
        item.identifier(),
        item.succeeded() ? "SUCCESS" : "FAILED",
        item.detail(),
        item.submissionId());
  }

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return 0;
  }
}
