package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
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
  void toCreateCommand_mapsAllFieldsFromFullyPopulatedRequest() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.status()).isEqualTo(req.getStatus().name());
    assertThat(command.laaReference()).isEqualTo(req.getLaaReference());
    assertThat(command.applicationContent()).isEqualTo(req.getApplicationContent());
    assertThat(command.serialisedRequest()).isNotNull().contains(req.getLaaReference());

    assertThat(command.individuals()).hasSize(req.getIndividuals().size());
    IndividualCreateRequest expectedIndividual = req.getIndividuals().getFirst();
    IndividualCommand mappedIndividual = command.individuals().getFirst();
    assertThat(mappedIndividual.firstName()).isEqualTo(expectedIndividual.getFirstName());
    assertThat(mappedIndividual.lastName()).isEqualTo(expectedIndividual.getLastName());
    assertThat(mappedIndividual.dateOfBirth()).isEqualTo(expectedIndividual.getDateOfBirth());
    assertThat(mappedIndividual.individualContent()).isEqualTo(expectedIndividual.getDetails());
    assertThat(mappedIndividual.type()).isEqualTo(expectedIndividual.getType().name());
  }

  @Test
  void toCommand_mapsIndividualFields_asIndividualCreateCommand() {
    IndividualCreateRequest individual =
        IndividualCreateRequest.builder()
            .firstName("Alice")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .details(Map.of("detail-key", "detail-value"))
            .type(IndividualType.CLIENT)
            .build();
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.individuals(List.of(individual)));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.individuals()).hasSize(1);
    IndividualCommand ind = command.individuals().getFirst();
    assertThat(ind.firstName()).isEqualTo("Alice");
    assertThat(ind.lastName()).isEqualTo("Smith");
    assertThat(ind.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
    assertThat(ind.individualContent()).isEqualTo(Map.of("detail-key", "detail-value"));
    assertThat(ind.type()).isEqualTo(IndividualType.CLIENT.name());
  }

  @Test
  void toCreateCommand_handlesNullStatus() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, b -> b.status(null));
    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);
    assertThat(command.status()).isNull();
  }

  @Test
  void toCreateCommand_handlesNullOrEmptyIndividuals() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.individuals(null));
    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);
    assertThat(command.individuals()).isEmpty();
  }

  @Test
  void toCreateCommand_mapsIndividualWithNullType_asNullType() {
    IndividualCreateRequest individual =
        IndividualCreateRequest.builder().firstName("Bob").lastName("Jones").type(null).build();
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.individuals(List.of(individual)));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    IndividualCommand ind = command.individuals().getFirst();
    assertThat(ind.type()).isNull();
  }

  @Test
  void toCreateCommand_mapsApplicationType_whenPresent() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.applicationType(ApplicationType.CCS));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.applicationType()).isEqualTo(ApplicationType.CCS.name());
  }

  @Test
  void toCreateCommand_defaultsApplicationTypeToApply_whenNull() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(
            ApplicationCreateRequestGenerator.class, b -> b.applicationType(null));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.applicationType()).isEqualTo(ApplicationType.APPLY.name());
  }
}
