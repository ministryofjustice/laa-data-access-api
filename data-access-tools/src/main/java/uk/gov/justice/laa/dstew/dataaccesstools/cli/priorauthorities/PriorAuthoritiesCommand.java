package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

@CommandLine.Command(
    name = "prior-authorities",
    mixinStandardHelpOptions = true,
    description = "Create and assign prior authorities.",
    subcommands = {
      CreatePriorAuthorityDraftsCommand.class,
      CreateSubmittedPriorAuthoritiesCommand.class,
      AssignPriorAuthorityCommand.class
    })
public final class PriorAuthoritiesCommand {
  @CommandLine.ParentCommand private DataAccessToolsCommand root;

  DataAccessToolsCommand root() {
    return root;
  }
}
