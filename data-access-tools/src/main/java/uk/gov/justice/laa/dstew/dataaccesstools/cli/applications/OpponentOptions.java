package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory.OpponentType;

/** Opponent generation options shared by the application creation commands. */
public final class OpponentOptions {
  @CommandLine.Spec(CommandLine.Spec.Target.MIXEE)
  private CommandLine.Model.CommandSpec spec;

  private int count = 1;

  @CommandLine.Option(
      names = "--opponent-type",
      converter = OpponentTypeConverter.class,
      description = "Force every opponent to be INDIVIDUAL or ORGANISATION (default: random).")
  private OpponentType type;

  @CommandLine.Option(
      names = "--opponent-count",
      defaultValue = "1",
      description = "Number of opponents to create on each application, 0 or more (default: 1).")
  void setCount(int count) {
    if (count < 0) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--opponent-count must not be negative");
    }
    this.count = count;
  }

  int count() {
    return count;
  }

  /** Null when each opponent's type should be randomised. */
  OpponentType type() {
    return type;
  }
}
