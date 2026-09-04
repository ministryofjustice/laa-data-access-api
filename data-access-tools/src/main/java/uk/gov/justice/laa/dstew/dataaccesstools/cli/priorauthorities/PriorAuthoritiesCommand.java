package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

@CommandLine.Command(
    name = "prior-authorities",
    description = "Create and assign prior authorities.",
    subcommands = {CreateAllPriorAuthoritiesCommand.class, AssignPriorAuthorityCommand.class})
public final class PriorAuthoritiesCommand {
  @CommandLine.ParentCommand private DataAccessToolsCommand root;

  DataAccessToolsCommand root() {
    return root;
  }
}
