package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.UnassignCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerUnassignRequestGenerator;

class UnassignCaseworkerCommandMapperTest {

  private final UnassignCaseworkerCommandMapper mapper = new UnassignCaseworkerCommandMapper();

  @Test
  void givenFullyPopulatedRequest_whenMapped_thenAllFieldsAreSet() {
    UUID id = UUID.randomUUID();
    CaseworkerUnassignRequest request =
        DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

    UnassignCaseworkerCommand command = mapper.toUnassignCaseworkerCommand(id, request);

    assertThat(command)
        .usingRecursiveComparison()
        .isEqualTo(
            UnassignCaseworkerCommand.builder()
                .applicationId(id)
                .eventDescription(request.getEventHistory().getEventDescription())
                .build());
  }

  @Test
  void givenNullEventHistory_whenMapped_thenEventDescriptionIsNull() {
    UUID id = UUID.randomUUID();
    CaseworkerUnassignRequest request = CaseworkerUnassignRequest.builder().build();

    UnassignCaseworkerCommand command = mapper.toUnassignCaseworkerCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.eventDescription()).isNull();
  }

  @Test
  void givenNullEventDescription_whenMapped_thenEventDescriptionIsNull() {
    UUID id = UUID.randomUUID();
    CaseworkerUnassignRequest request =
        CaseworkerUnassignRequest.builder()
            .eventHistory(EventHistoryRequest.builder().eventDescription(null).build())
            .build();

    UnassignCaseworkerCommand command = mapper.toUnassignCaseworkerCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.eventDescription()).isNull();
  }
}
