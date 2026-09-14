package uk.gov.justice.laa.dstew.dataaccesstools.cli.local;

import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "application-events",
    mixinStandardHelpOptions = true,
    description = "Display the complete local Axon event stream for an application.")
public final class ApplicationEventsCommand extends LocalEventStoreCommand
    implements Callable<Integer> {
  @CommandLine.Option(
      names = "--application-id",
      required = true,
      description = "Application UUID.")
  private UUID applicationId;

  @Override
  public Integer call() {
    return printEvents("application", applicationId);
  }
}
