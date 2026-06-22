package uk.gov.justice.laa.dstew.access.utils.generator.createapplication;

import java.time.LocalDate;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.IndividualCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class IndividualCommandGenerator
    extends BaseGenerator<IndividualCommand, IndividualCommand.IndividualCommandBuilder> {

  public IndividualCommandGenerator() {
    super(IndividualCommand::toBuilder, IndividualCommand.IndividualCommandBuilder::build);
  }

  @Override
  public IndividualCommand createDefault() {
    return IndividualCommand.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.of(1990, 1, 1))
        .individualContent(Map.of("test", "content"))
        .type("CLIENT")
        .build();
  }
}
