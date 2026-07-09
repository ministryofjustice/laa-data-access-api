package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationUpdateRequestGenerator;

class UpdateApplicationCommandMapperTest {

  private static final UpdateApplicationCommandMapper MAPPER = new UpdateApplicationCommandMapper();

  @Test
  void givenFullyPopulatedRequest_whenMapped_thenAllFieldsCorrect() {
    UUID id = UUID.randomUUID();
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class,
            builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

    UpdateApplicationCommand actualCommand =
        MAPPER.toUpdateApplicationCommand(id, applicationUpdateRequest);

    UpdateApplicationCommand expectedCommand =
        UpdateApplicationCommand.builder()
            .id(id)
            .status(ApplicationStatus.APPLICATION_SUBMITTED.name())
            .applicationContent(applicationUpdateRequest.getApplicationContent())
            .build();

    assertThat(actualCommand).usingRecursiveComparison().isEqualTo(expectedCommand);
  }

  @Test
  void givenNullStatusField_whenMapped_thenStatusIsNull() {
    UUID id = UUID.randomUUID();
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class, builder -> builder.status(null));

    UpdateApplicationCommand actualCommand =
        MAPPER.toUpdateApplicationCommand(id, applicationUpdateRequest);

    assertThat(actualCommand.status()).isNull();
    assertThat(actualCommand.id()).isEqualTo(id);
    assertThat(actualCommand.applicationContent())
        .usingRecursiveComparison()
        .isEqualTo(applicationUpdateRequest.getApplicationContent());
  }
}
