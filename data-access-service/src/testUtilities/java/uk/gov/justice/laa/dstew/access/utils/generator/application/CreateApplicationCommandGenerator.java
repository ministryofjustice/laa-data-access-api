package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualDomainGenerator;

public class CreateApplicationCommandGenerator
    extends BaseGenerator<
        CreateApplicationCommand, CreateApplicationCommand.CreateApplicationCommandBuilder> {

  private final ApplicationContentDomainGenerator applicationContentGenerator =
      new ApplicationContentDomainGenerator();
  private final IndividualDomainGenerator individualGenerator = new IndividualDomainGenerator();

  public CreateApplicationCommandGenerator() {
    super(
        CreateApplicationCommand::toBuilder,
        CreateApplicationCommand.CreateApplicationCommandBuilder::build);
  }

  @Override
  public CreateApplicationCommand createDefault() {
    ObjectMapper mapper = new ObjectMapper();
    var content = applicationContentGenerator.createDefault();
    return CreateApplicationCommand.builder()
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("REF7327")
        .individuals(List.of(individualGenerator.createDefault()))
        .applicationContent(mapper.convertValue(content, Map.class))
        .serialisedRequest(serialise(mapper, content))
        .build();
  }

  private String serialise(ObjectMapper mapper, Object obj) {
    try {
      return mapper.writeValueAsString(obj);
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalArgumentException("Failed to serialise content", e);
    }
  }
}
