package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.IndividualCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;

class CreateApplicationCommandMapperTest {

  private CreateApplicationCommandMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new CreateApplicationCommandMapper(new ObjectMapper());
  }

  @Test
  void toCommand_mapsAllFieldsFromFullyPopulatedRequest() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    CreateApplicationCommand command = mapper.toCommand(req);

    assertThat(command.status()).isEqualTo(req.getStatus().name());
    assertThat(command.laaReference()).isEqualTo(req.getLaaReference());
    assertThat(command.applicationContent()).isEqualTo(req.getApplicationContent());
    assertThat(command.individuals()).hasSize(req.getIndividuals().size());
    assertThat(command.serialisedRequest()).isNotNull().contains(req.getLaaReference());
  }

  @Test
  void toCommand_mapsIndividualFields_asIndividualCommand() {
    IndividualCreateRequest individual =
        IndividualCreateRequest.builder()
            .firstName("Alice")
            .lastName("Smith")
            .type(IndividualType.CLIENT)
            .build();
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.individuals(List.of(individual)));

    CreateApplicationCommand command = mapper.toCommand(req);

    assertThat(command.individuals()).hasSize(1);
    IndividualCommand ind = command.individuals().get(0);
    assertThat(ind.firstName()).isEqualTo("Alice");
    assertThat(ind.lastName()).isEqualTo("Smith");
    assertThat(ind.type()).isEqualTo(IndividualType.CLIENT.name());
  }

  @Test
  void toCommand_handlesNullStatus() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, b -> b.status(null));
    CreateApplicationCommand command = mapper.toCommand(req);
    assertThat(command.status()).isNull();
  }

  @Test
  void toCommand_handlesNullOrEmptyIndividuals() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.individuals(null));
    CreateApplicationCommand command = mapper.toCommand(req);
    assertThat(command.individuals()).isEmpty();
  }
}
