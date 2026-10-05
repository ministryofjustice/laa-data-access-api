package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

@CommandLine.Command(
    name = "applications",
    mixinStandardHelpOptions = true,
    description = "Create applications, decisions, and assignments.",
    subcommands = {
      CreateApplicationsCommand.class,
      CreateApplicationDraftCommand.class,
      SubmitApplicationDraftCommand.class,
      MakeDecisionCommand.class,
      AssignApplicationCommand.class
    })
public final class ApplicationsCommand {
  @CommandLine.ParentCommand private DataAccessToolsCommand root;

  DataAccessToolsCommand root() {
    return root;
  }

  ApplicationCreationWorkflow creationWorkflow() {
    return creationWorkflow(null);
  }

  ApplicationCreationWorkflow creationWorkflow(String officeCode) {
    return new ApplicationCreationWorkflow(
        root.client(), new ApplicationRequestFactory(root.seed(), officeCode), new DecisionRequestFactory());
  }
}
