package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.PotentialDuplicateTestBuilder;

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

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithValidData() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    List<PotentialDuplicate> duplicates = List.of(
        PotentialDuplicateTestBuilder.builder()
            .withLaaReference("LAA-12345")
            .withApplicationId(UUID.randomUUID())
            .withLegacyReference("LEGACY-999")
            .build(),
        PotentialDuplicateTestBuilder.builder()
            .withLaaReference("LAA-67890")
            .withApplicationId(UUID.randomUUID())
            .withLegacyReference("LEGACY-888")
            .build()
    );
    req.setPotentialDuplicates(duplicates);

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates())
        .isNotNull()
        .hasSize(2)
        .extracting(PotentialDuplicate::getLaaReference)
        .containsExactly("LAA-12345", "LAA-67890");
    assertThat(command.potentialDuplicates().get(0).getApplicationId()).isNotNull();
    assertThat(command.potentialDuplicates().get(0).getLegacyReference()).isEqualTo("LEGACY-999");
  }

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithOnlyRequiredField() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    List<PotentialDuplicate> duplicates = List.of(
        new PotentialDuplicate().laaReference("LAA-12345")
    );
    req.setPotentialDuplicates(duplicates);

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates())
        .isNotNull()
        .hasSize(1);
    assertThat(command.potentialDuplicates().get(0).getLaaReference()).isEqualTo("LAA-12345");
    assertThat(command.potentialDuplicates().get(0).getApplicationId()).isNull();
    assertThat(command.potentialDuplicates().get(0).getLegacyReference()).isNull();
  }

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithNullApplicationId() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    PotentialDuplicate duplicate = new PotentialDuplicate();
    duplicate.setLaaReference("LAA-12345");
    duplicate.setApplicationId(null);
    duplicate.setLegacyReference("LEGACY-999");
    req.setPotentialDuplicates(List.of(duplicate));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates().get(0).getApplicationId()).isNull();
    assertThat(command.potentialDuplicates().get(0).getLegacyReference()).isEqualTo("LEGACY-999");
  }

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithNullLegacyReference() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    UUID appId = UUID.randomUUID();
    PotentialDuplicate duplicate = new PotentialDuplicate();
    duplicate.setLaaReference("LAA-12345");
    duplicate.setApplicationId(appId);
    duplicate.setLegacyReference(null);
    req.setPotentialDuplicates(List.of(duplicate));

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates().get(0).getApplicationId()).isEqualTo(appId);
    assertThat(command.potentialDuplicates().get(0).getLegacyReference()).isNull();
  }

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithEmptyList() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    req.setPotentialDuplicates(new ArrayList<>());

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates()).isNotNull().isEmpty();
  }

  @Test
  void toCreateCommand_mapsPotentialDuplicates_WithNull() {
    ApplicationCreateRequest req =
        DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);
    req.setPotentialDuplicates(null);

    CreateApplicationCommand command = mapper.toCreateCommand(req, 1);

    assertThat(command.potentialDuplicates()).isNull();
  }
}
