package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "submit-draft",
    mixinStandardHelpOptions = true,
    description = "Submit an existing application draft, preserving uploaded evidence.")
public final class SubmitApplicationDraftCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Application draft ID.")
  private UUID applicationId;

  @Override
  public Integer call() {
    return applications.root().print(applications.creationWorkflow().submitDraft(applicationId));
  }
}
