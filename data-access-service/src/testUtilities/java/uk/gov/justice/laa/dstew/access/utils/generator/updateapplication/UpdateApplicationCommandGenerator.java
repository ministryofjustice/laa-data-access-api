package uk.gov.justice.laa.dstew.access.utils.generator.updateapplication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class UpdateApplicationCommandGenerator
    extends BaseGenerator<
        UpdateApplicationCommand, UpdateApplicationCommand.UpdateApplicationCommandBuilder> {

  public UpdateApplicationCommandGenerator() {
    super(
        UpdateApplicationCommand::toBuilder,
        UpdateApplicationCommand.UpdateApplicationCommandBuilder::build);
  }

  @Override
  public UpdateApplicationCommand createDefault() {
    return UpdateApplicationCommand.builder()
        .id(UUID.randomUUID())
        .status("APPLICATION_IN_PROGRESS")
        .applicationContent(new HashMap<>(Map.of("test", "changed")))
        .build();
  }
}
