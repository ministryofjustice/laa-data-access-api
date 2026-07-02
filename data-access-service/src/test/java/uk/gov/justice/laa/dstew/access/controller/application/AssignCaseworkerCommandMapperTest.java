package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerCommand;

class AssignCaseworkerCommandMapperTest {

  private AssignCaseworkerCommandMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new AssignCaseworkerCommandMapper();
  }

  @Test
  void givenFullyPopulatedRequest_whenMapped_thenAllFieldsMapped() {
    UUID caseworkerId = UUID.randomUUID();
    List<UUID> applicationIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    String eventDescription = "Caseworker assigned.";
    CaseworkerAssignRequest request =
        CaseworkerAssignRequest.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(applicationIds)
            .eventHistory(EventHistoryRequest.builder().eventDescription(eventDescription).build())
            .build();

    AssignCaseworkerCommand command = mapper.toAssignCaseworkerCommand(request);

    AssignCaseworkerCommand expected =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(applicationIds)
            .eventDescription(eventDescription)
            .build();
    assertThat(command).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenNullEventHistory_whenMapped_thenEventDescriptionIsNull() {
    CaseworkerAssignRequest request =
        CaseworkerAssignRequest.builder()
            .caseworkerId(UUID.randomUUID())
            .applicationIds(List.of(UUID.randomUUID()))
            .build();

    AssignCaseworkerCommand command = mapper.toAssignCaseworkerCommand(request);

    assertThat(command.eventDescription()).isNull();
  }
}
