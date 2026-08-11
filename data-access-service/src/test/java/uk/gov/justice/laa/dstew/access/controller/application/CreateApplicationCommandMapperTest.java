package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;

class CreateApplicationCommandMapperTest {

  private CreateApplicationCommandMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new CreateApplicationCommandMapper(new ObjectMapper());
  }

  @Test
  void toCreateCommand_mapsAllFieldsFromFullyPopulatedRequest() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.id()).isEqualTo(req.getId());
    assertThat(command.status()).isEqualTo(req.getStatus().name());
    assertThat(command.laaReference()).isEqualTo(req.getLaaReference());
    assertThat(command.applicationContent()).isEqualTo(req.getApplicationContent());
    assertThat(command.serialisedRequest()).isNotNull().contains(req.getLaaReference());
  }

  @Test
  void toCreateCommand_handlesNullStatus() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, b -> b.status(null));
    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);
    assertThat(command.status()).isNull();
  }
}
