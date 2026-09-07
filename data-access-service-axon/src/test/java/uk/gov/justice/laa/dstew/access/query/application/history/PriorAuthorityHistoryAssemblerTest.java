package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PriorAuthorityHistoryAssemblerTest {

  private final PriorAuthorityHistoryAssembler assembler = new PriorAuthorityHistoryAssembler();

  // --- grouping and ordering ---

  @Test
  void givenRowsForTwoSubmissions_whenAssembled_thenOneGroupPerSubmissionId() {
    UUID applicationId = UUID.randomUUID();
    UUID firstSubmissionId = UUID.randomUUID();
    UUID secondSubmissionId = UUID.randomUUID();
    var rows =
        List.of(
            row(
                applicationId,
                firstSubmissionId,
                "EXPERT",
                "evt-a",
                Instant.parse("2026-08-01T10:00:00Z")),
            row(
                applicationId,
                secondSubmissionId,
                "COUNSEL",
                "evt-b",
                Instant.parse("2026-08-01T11:00:00Z")));

    var groups = assembler.assemble(rows);

    assertThat(groups)
        .extracting(PriorAuthorityHistoryGroupResult::submissionId)
        .containsExactly(firstSubmissionId, secondSubmissionId);
  }

  @Test
  void
      givenMultipleEventsForOneSubmission_whenAssembled_thenEventsOrderedByOccurredAtThenEventId() {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var earlierRow =
        row(
            applicationId,
            submissionId,
            "EXPERT",
            "evt-aaa",
            Instant.parse("2026-08-01T09:00:00Z"));
    // Both later rows have the same timestamp but distinct service names, making the tie-break
    // observable.
    var firstLaterRow =
        row(
            applicationId,
            submissionId,
            "EXPERT",
            "evt-aab",
            Instant.parse("2026-08-01T10:00:00Z"),
            "SERVICE_A");
    var secondLaterRow =
        row(
            applicationId,
            submissionId,
            "EXPERT",
            "evt-aac",
            Instant.parse("2026-08-01T10:00:00Z"),
            "SERVICE_B");

    var groups = assembler.assemble(List.of(secondLaterRow, firstLaterRow, earlierRow));

    assertThat(groups)
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.events())
                  .extracting(PriorAuthorityHistoryEventResult::serviceName)
                  .containsExactly("CIVIL_APPLY", "SERVICE_A", "SERVICE_B");
              assertThat(group.events())
                  .extracting(PriorAuthorityHistoryEventResult::occurredAt)
                  .containsExactly(
                      Instant.parse("2026-08-01T09:00:00Z"),
                      Instant.parse("2026-08-01T10:00:00Z"),
                      Instant.parse("2026-08-01T10:00:00Z"));
            });
  }

  @Test
  void givenTwoSubmissions_whenAssembled_thenGroupOrderFollowsEarliestEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID laterSubmissionId = UUID.randomUUID();
    UUID earlierSubmissionId = UUID.randomUUID();
    var rows =
        List.of(
            row(
                applicationId,
                laterSubmissionId,
                "EXPERT",
                "evt-1",
                Instant.parse("2026-08-01T12:00:00Z")),
            row(
                applicationId,
                earlierSubmissionId,
                "COUNSEL",
                "evt-2",
                Instant.parse("2026-08-01T08:00:00Z")));

    var groups = assembler.assemble(rows);

    assertThat(groups)
        .extracting(PriorAuthorityHistoryGroupResult::submissionId)
        .containsExactly(earlierSubmissionId, laterSubmissionId);
  }

  @Test
  void givenEmptyInput_whenAssembled_thenReturnsEmptyList() {
    assertThat(assembler.assemble(List.of())).isEmpty();
  }

  // --- immutability ---

  @Test
  void givenValidRows_whenAssembled_thenReturnedGroupListIsUnmodifiable() {
    UUID applicationId = UUID.randomUUID();
    var groups =
        assembler.assemble(
            List.of(
                row(
                    applicationId,
                    UUID.randomUUID(),
                    "EXPERT",
                    "e1",
                    Instant.parse("2026-08-01T10:00:00Z"))));

    assertThatThrownBy(() -> groups.add(null)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenValidRows_whenAssembled_thenEventsListIsUnmodifiable() {
    UUID applicationId = UUID.randomUUID();
    var groups =
        assembler.assemble(
            List.of(
                row(
                    applicationId,
                    UUID.randomUUID(),
                    "EXPERT",
                    "e1",
                    Instant.parse("2026-08-01T10:00:00Z"))));

    assertThatThrownBy(() -> groups.getFirst().events().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // --- event hydration ---

  @Test
  void givenPriorAuthorityCreatedRow_whenAssembled_thenEventDescriptionIsNull() {
    UUID applicationId = UUID.randomUUID();
    var groups =
        assembler.assemble(
            List.of(
                row(
                    applicationId,
                    UUID.randomUUID(),
                    "EXPERT",
                    "e1",
                    Instant.parse("2026-08-01T10:00:00Z"))));

    assertThat(groups.getFirst().events().getFirst().eventDescription()).isNull();
  }

  @Test
  void givenRowWithNullServiceName_whenAssembled_thenServiceNameIsPreservedAsNull() {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var historyRow =
        PriorAuthorityHistoryReadModel.builder()
            .eventId("e1")
            .applicationId(applicationId)
            .submissionId(submissionId)
            .priorAuthorityType("EXPERT")
            .eventType("PRIOR_AUTHORITY_CREATED")
            .eventData("{}")
            .serviceName(null)
            .occurredAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build();

    var groups = assembler.assemble(List.of(historyRow));

    assertThat(groups.getFirst().events().getFirst().serviceName()).isNull();
  }

  // --- integrity: conflicting types ---

  @Test
  void
      givenTwoRowsSameSubmissionDifferentTypes_whenAssembled_thenThrowsApplicationHistoryIntegrityException() {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var expertHistoryRow =
        row(applicationId, submissionId, "EXPERT", "e1", Instant.parse("2026-08-01T09:00:00Z"));
    var counselHistoryRow =
        row(applicationId, submissionId, "COUNSEL", "e2", Instant.parse("2026-08-01T10:00:00Z"));

    assertThatThrownBy(() -> assembler.assemble(List.of(expertHistoryRow, counselHistoryRow)))
        .isInstanceOf(ApplicationHistoryIntegrityException.class)
        .satisfies(
            throwable -> {
              var integrityException = (ApplicationHistoryIntegrityException) throwable;
              assertThat(integrityException.getApplicationId()).isEqualTo(applicationId);
              assertThat(integrityException.getSubmissionId()).isEqualTo(submissionId);
              assertThat(integrityException.getReason()).contains("conflicting");
            });
  }

  // --- single-value pass-through ---

  @ParameterizedTest(name = "preserves single prior authority type [{0}]")
  @ValueSource(strings = {"EXPERT", "COUNSEL", "DISBURSEMENT", "", "   ", "UNKNOWN_TYPE"})
  void givenSinglePriorAuthorityType_whenAssembled_thenGroupPreservesType(
      String priorAuthorityType) {
    var groups =
        assembler.assemble(
            List.of(
                row(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    priorAuthorityType,
                    "e1",
                    Instant.parse("2026-08-01T10:00:00Z"))));

    assertThat(groups)
        .extracting(PriorAuthorityHistoryGroupResult::priorAuthorityType)
        .containsExactly(priorAuthorityType);
  }

  // --- helpers ---

  private PriorAuthorityHistoryReadModel row(
      UUID applicationId,
      UUID submissionId,
      String priorAuthorityType,
      String eventId,
      Instant occurredAt) {
    return row(applicationId, submissionId, priorAuthorityType, eventId, occurredAt, "CIVIL_APPLY");
  }

  private PriorAuthorityHistoryReadModel row(
      UUID applicationId,
      UUID submissionId,
      String priorAuthorityType,
      String eventId,
      Instant occurredAt,
      String serviceName) {
    return PriorAuthorityHistoryReadModel.builder()
        .eventId(eventId)
        .applicationId(applicationId)
        .submissionId(submissionId)
        .priorAuthorityType(priorAuthorityType)
        .eventType("PRIOR_AUTHORITY_CREATED")
        .eventData("{\"status\":\"PENDING\",\"dataVersion\":0}")
        .serviceName(serviceName)
        .occurredAt(occurredAt)
        .build();
  }
}
