package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationCreationWorkflow.Outcome;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory.OpponentType;

@CommandLine.Command(
    name = "create",
    mixinStandardHelpOptions = true,
    description = "Create and submit complete application drafts, optionally applying an outcome.")
public final class CreateApplicationsCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--count",
      required = true,
      description = "Number of applications to create.")
  private int count;

  @CommandLine.Option(
      names = "--outcome",
      defaultValue = "submitted",
      converter = OutcomeConverter.class,
      description = "Outcome: submitted (default), manual, autogranted, granted, refused.")
  private Outcome outcome;

  @CommandLine.Option(
      names = "--office-code",
      converter = OfficeCodeConverter.class,
      description = "Provider office code to use for every created application.")
  private String officeCode;

  @CommandLine.Option(
      names = "--opponent-count",
      defaultValue = "1",
      description = "Number of opponents to create on each application, 0 or more (default: 1).")
  private int opponentCount;

  @CommandLine.Option(
      names = "--opponent-type",
      converter = OpponentTypeConverter.class,
      description = "Force every opponent to be INDIVIDUAL or ORGANISATION (default: random).")
  private OpponentType opponentType;

  @Override
  public Integer call() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
    if (opponentCount < 0) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--opponent-count must not be negative");
    }
    return applications
        .root()
        .print(
            applications
                .creationWorkflow(officeCode, opponentCount, opponentType)
                .create(count, outcome));
  }

  static final class OutcomeConverter implements CommandLine.ITypeConverter<Outcome> {
    @Override
    public Outcome convert(String value) {
      return Outcome.valueOf(value.toUpperCase(Locale.ROOT));
    }
  }
}
