package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationCreationWorkflow.Outcome;

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

  @Override
  public Integer call() {
    if (count < 1) {
      throw new CommandLine.ParameterException(new CommandLine(this), "--count must be positive");
    }
    return applications
        .root()
        .print(applications.creationWorkflow(officeCode).create(count, outcome));
  }

  static final class OutcomeConverter implements CommandLine.ITypeConverter<Outcome> {
    @Override
    public Outcome convert(String value) {
      return Outcome.valueOf(value.toUpperCase(Locale.ROOT));
    }
  }
}
