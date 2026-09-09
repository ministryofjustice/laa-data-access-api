package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "make-decision",
    mixinStandardHelpOptions = true,
    description = "Make a granted or refused decision for an application.")
public final class MakeDecisionCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Application UUID.")
  private UUID applicationId;

  @CommandLine.Option(
      names = "--decision",
      required = true,
      description = "Overall decision: ${COMPLETION-CANDIDATES}.")
  private DecisionRequestFactory.Decision decision;

  @Override
  public Integer call() {
    var client = applications.root().client();
    client.makeDecision(
        applicationId,
        new DecisionRequestFactory().create(client.getApplicationDecisionData(applicationId), decision));
    System.out.printf("%s: SUCCESS - %s%n", applicationId, decision);
    return 0;
  }
}

