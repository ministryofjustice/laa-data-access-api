package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;

@ExtendWith(MockitoExtension.class)
class ApplicationHistoryAssemblerTest {

  @Mock private ApplicationDataStore applicationDataStore;

  @InjectMocks private ApplicationHistoryAssembler assembler;

  @Test
  void givenNoteCreatedRow_whenAssembled_thenHydratesLatestNoteText() {
    UUID applicationId = UUID.randomUUID();
    var row = row(applicationId, "APPLICATION_NOTE_CREATED", 3L);
    var payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId))
            .withNote("First note", Instant.parse("2026-07-19T09:00:00Z"))
            .withNote("Latest note", Instant.parse("2026-07-19T10:00:00Z"));
    when(applicationDataStore.findPayload(applicationId, 3L)).thenReturn(Optional.of(payload));

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(event -> assertThat(event.eventDescription()).isEqualTo("Latest note"));
  }

  @Test
  void givenNoteCreatedRowWithNoNotes_whenAssembled_thenDescriptionIsNull() {
    UUID applicationId = UUID.randomUUID();
    var row = row(applicationId, "APPLICATION_NOTE_CREATED", 3L);
    var payload = ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.findPayload(applicationId, 3L)).thenReturn(Optional.of(payload));

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(event -> assertThat(event.eventDescription()).isNull());
  }

  @Test
  void givenDecisionGrantedRow_whenAssembled_thenHydratesDecisionDescription() {
    UUID applicationId = UUID.randomUUID();
    var row = row(applicationId, "APPLICATION_MAKE_DECISION_GRANTED", 4L);
    var payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId))
            .withDecision(
                "GRANTED", AutoGrantedState.MANUAL, Map.of(), null, "{}", "Decision recorded");
    when(applicationDataStore.findPayload(applicationId, 4L)).thenReturn(Optional.of(payload));

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(event -> assertThat(event.eventDescription()).isEqualTo("Decision recorded"));
  }

  @Test
  void givenMissingDecisionDataVersion_whenAssembled_thenDescriptionIsNull() {
    UUID applicationId = UUID.randomUUID();
    var row = row(applicationId, "APPLICATION_MAKE_DECISION_GRANTED", 4L);
    when(applicationDataStore.findPayload(applicationId, 4L)).thenReturn(Optional.empty());

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(event -> assertThat(event.eventDescription()).isNull());
  }

  @Test
  void givenUnexpectedDataStoreFailure_whenAssembled_thenFailureIsPropagated() {
    UUID applicationId = UUID.randomUUID();
    var row = row(applicationId, "APPLICATION_NOTE_CREATED", 3L);
    var failure = new IllegalStateException("database is unavailable");
    when(applicationDataStore.findPayload(applicationId, 3L)).thenThrow(failure);

    assertThatIllegalStateException()
        .isThrownBy(() -> assembler.assemble(List.of(row)))
        .isSameAs(failure);
  }

  @Test
  void givenAssignmentRowWithNoDataVersion_whenAssembled_thenDescriptionIsNull() {
    var row = row(UUID.randomUUID(), "ASSIGN_APPLICATION_TO_CASEWORKER", null);

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(event -> assertThat(event.eventDescription()).isNull());
    verifyNoInteractions(applicationDataStore);
  }

  @Test
  void givenCreatedRow_whenAssembled_thenCarriesThroughMetadataFieldsUnchanged() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:00:00Z");
    var row =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(applicationId)
            .eventType("APPLICATION_CREATED")
            .dataVersion(1L)
            .serviceName("CIVIL_APPLY")
            .caseworkerId(caseworkerId)
            .occurredAt(occurredAt)
            .build();

    var results = assembler.assemble(List.of(row));

    assertThat(results)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.applicationId()).isEqualTo(applicationId);
              assertThat(event.eventType()).isEqualTo("APPLICATION_CREATED");
              assertThat(event.occurredAt()).isEqualTo(occurredAt);
              assertThat(event.serviceName()).isEqualTo("CIVIL_APPLY");
              assertThat(event.caseworkerId()).isEqualTo(caseworkerId);
              assertThat(event.eventDescription()).isNull();
            });
    verifyNoInteractions(applicationDataStore);
  }

  @Test
  void givenEmptyRows_whenAssembled_thenReturnsEmptyList() {
    assertThat(assembler.assemble(List.of())).isEmpty();
    verifyNoInteractions(applicationDataStore);
  }

  private ApplicationHistoryReadModel row(UUID applicationId, String eventType, Long dataVersion) {
    return ApplicationHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(applicationId)
        .eventType(eventType)
        .dataVersion(dataVersion)
        .serviceName("CIVIL_APPLY")
        .occurredAt(Instant.parse("2026-07-19T10:00:00Z"))
        .build();
  }
}
