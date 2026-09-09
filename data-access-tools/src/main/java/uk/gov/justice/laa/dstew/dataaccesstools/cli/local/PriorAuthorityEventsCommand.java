package uk.gov.justice.laa.dstew.dataaccesstools.cli.local;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "prior-authority-events",
    mixinStandardHelpOptions = true,
    description = "Display the complete local Axon event stream for a prior authority.")
public final class PriorAuthorityEventsCommand extends LocalEventStoreCommand
    implements Callable<Integer> {
  @CommandLine.Option(
      names = "--prior-authority-id",
      required = true,
      description = "Prior-authority submission UUID.")
  private UUID priorAuthorityId;

  @Override
  public Integer call() {
    return printEvents("prior authority", priorAuthorityId);
  }
}
