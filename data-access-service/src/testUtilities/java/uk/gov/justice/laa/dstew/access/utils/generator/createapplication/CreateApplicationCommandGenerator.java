package uk.gov.justice.laa.dstew.access.utils.generator.createapplication;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;

public class CreateApplicationCommandGenerator
    extends BaseGenerator<
        CreateApplicationCommand, CreateApplicationCommand.CreateApplicationCommandBuilder> {

  private final IndividualCommandGenerator individualCommandGenerator =
      new IndividualCommandGenerator();

  public CreateApplicationCommandGenerator() {
    super(
        CreateApplicationCommand::toBuilder,
        CreateApplicationCommand.CreateApplicationCommandBuilder::build);
  }

  @Override
  public CreateApplicationCommand createDefault() {
    ObjectMapper mapper = new ObjectMapper();
    ApplicationContentGenerator contentGen = new ApplicationContentGenerator();
    @SuppressWarnings("unchecked")
    Map<String, Object> appContent = mapper.convertValue(contentGen.createDefault(), Map.class);

    return CreateApplicationCommand.builder()
        .status("APPLICATION_IN_PROGRESS")
        .laaReference("REF7327")
        .applicationContent(appContent)
        .individuals(List.of(individualCommandGenerator.createDefault()))
        .serialisedRequest("{\"status\":\"APPLICATION_IN_PROGRESS\",\"laaReference\":\"REF7327\"}")
        .schemaVersion(1)
        .applicationType("APPLY")
        .build();
  }
}
