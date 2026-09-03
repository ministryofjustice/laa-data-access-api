package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

@CommandLine.Command(
    name = "applications",
    description = "Create applications, decisions, and assignments.",
    subcommands = {
      CreateAutograntedApplicationsCommand.class,
      CreateGrantedApplicationsCommand.class,
      CreateRefusedApplicationsCommand.class,
      CreateManualApplicationsCommand.class,
      AssignApplicationCommand.class
    })
public final class ApplicationsCommand {
  @CommandLine.ParentCommand private DataAccessToolsCommand root;

  DataAccessToolsCommand root() {
    return root;
  }
}
