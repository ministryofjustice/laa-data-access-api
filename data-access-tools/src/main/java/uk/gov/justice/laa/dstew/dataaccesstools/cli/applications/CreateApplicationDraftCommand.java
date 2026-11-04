package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory.OpponentType;

@CommandLine.Command(
    name = "create-draft",
    mixinStandardHelpOptions = true,
    description = "Create complete application drafts ready for evidence uploads or submission.")
public final class CreateApplicationDraftCommand implements Callable<Integer> {
  @CommandLine.ParentCommand private ApplicationsCommand applications;

  @CommandLine.Option(
      names = "--count",
      required = true,
      description = "Number of drafts to create.")
  private int count;

  @CommandLine.Option(
      names = "--office-code",
      converter = OfficeCodeConverter.class,
      description = "Provider office code to use for every created application.")
  private String officeCode;

  @CommandLine.Option(
      names = "--opponent-count",
      defaultValue = "1",
      description = "Number of opponents to create on each application (default: 1).")
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
    if (opponentCount < 1) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--opponent-count must be positive");
    }
    return applications
        .root()
        .print(
            applications
                .creationWorkflow(officeCode, opponentCount, opponentType)
                .createDrafts(count));
  }
}
