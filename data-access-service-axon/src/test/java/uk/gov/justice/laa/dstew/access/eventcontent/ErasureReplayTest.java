package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.testutils.EventTypeNames;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/**
 * With all content erased, every aggregate must load, and every command must decide the same way.
 * Each history is folded twice through the real evolve functions: once as written, once after
 * {@link EventContentSeparator#erase}.
 */
class ErasureReplayTest {

  private static final Instant AT = Instant.parse("2026-11-02T10:00:00Z");
  private static final String DECISION_REQUEST = "{\"overallDecision\":\"GRANTED\"}";

  private final EventContentSeparator separator = new EventContentSeparator(new EventJson());

  private final UUID applicationId = UUID.randomUUID();
  private final UUID priorAuthorityId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final UUID deletedDocumentId = UUID.randomUUID();
  private final UUID caseworkerId = UUID.randomUUID();
  private final UUID proceedingId = UUID.randomUUID();

  @Test
  void givenApplicationHistory_whenContentErased_thenOnlyContentFieldsDifferAndDecisionsMatch() {
    List<Object> history =
        List.of(
            created(),
            new ApplicationDocumentUploadedEvent(
                applicationId,
                documentId,
                Erasable.of("GATEWAY_EVIDENCE"),
                AT,
                Erasable.of(12L),
                Erasable.of("application/pdf"),
                "checksum",
                Erasable.of("CIVIL_APPLY"),
                Erasable.of(".pdf"),
                Erasable.of("evidence.pdf")),
            new WorkItemAssigned(
                applicationId, WorkItemType.APPLICATION, applicationId, 0L, 1L, caseworkerId, AT),
            new NoteCreatedEvent(applicationId, AT, "private note"));

    ApplicationState full = fold(new ApplicationState(), ApplicationEvolve.class, history);
    ApplicationState erased = fold(new ApplicationState(), ApplicationEvolve.class, erase(history));

    assertThat(erased.getUploadedDocuments()).hasSize(1);
    assertThat(erased)
        .usingRecursiveComparison()
        .ignoringFields(
            "uploadedDocuments", "draftDocumentFilenames", "content", "potentialDuplicates")
        .isEqualTo(full);
    assertThat(erased.getPotentialDuplicates().isErased()).isTrue();
    assertThat(full.getContent().isErased()).isFalse();
    assertThat(erased.getContent().isErased()).isTrue();
    assertThat(erased.getProceedingIds()).containsExactly(proceedingId);
    assertThat(erased.getUploadedDocuments())
        .isEqualTo(full.getUploadedDocuments().stream().map(this::withoutContent).toList());

    assertSameDecision(
        full,
        erased,
        state ->
            ApplicationDecider.decideReady(
                state, new MarkApplicationReadyCommand(applicationId, 0L, "{}", AT)));
    assertSameDecision(
        full,
        erased,
        state ->
            ApplicationDecider.decideNote(
                state, new CreateNoteCommand(applicationId, "note", "{}", AT)));
    assertSameDecision(full, erased, state -> decide(state, 1L, proceedingId));
    assertSameDecision(full, erased, state -> decide(state, 1L, UUID.randomUUID()));
    assertSameDecision(full, erased, state -> decide(state, 0L, proceedingId));
  }

  @Test
  void givenDraftHistory_whenContentErased_thenOnlyContentFieldsDifferAndDecisionsMatch() {
    List<Object> history =
        List.of(
            draftStarted("fingerprint"),
            new ApplicationDocumentUploadedEvent(
                applicationId,
                documentId,
                Erasable.of("GATEWAY_EVIDENCE"),
                AT,
                Erasable.of(12L),
                Erasable.of("application/pdf"),
                "checksum",
                Erasable.of("CIVIL_APPLY"),
                Erasable.of(".pdf"),
                Erasable.of("evidence.pdf")));

    ApplicationState full = fold(new ApplicationState(), ApplicationEvolve.class, history);
    ApplicationState erased = fold(new ApplicationState(), ApplicationEvolve.class, erase(history));

    assertThat(erased)
        .usingRecursiveComparison()
        .ignoringFields(
            "uploadedDocuments",
            "draftApplicationContent",
            "draftSerialisedRequest",
            "draftPotentialDuplicates",
            "draftDocumentFilenames")
        .isEqualTo(full);
    assertThat(erased.getDraftApplicationContent().isErased()).isTrue();
    assertThat(erased.getDraftSerialisedRequest().isErased()).isTrue();
    assertThat(erased.getDraftPotentialDuplicates().isErased()).isTrue();
    assertThat(erased.getDraftDocumentFilenames())
        .containsOnlyKeys(documentId)
        .allSatisfy((id, filename) -> assertThat(filename.isErased()).isTrue());

    assertSameDecision(
        full, erased, state -> ApplicationDecider.decideStartDraft(state, 1, "fingerprint"));
    assertSameDecision(
        full, erased, state -> ApplicationDecider.decideStartDraft(state, 1, "different"));
    assertSameDecision(
        full, erased, state -> ApplicationDecider.decideStartDraft(state, 2, "fingerprint"));
    assertSameDecision(
        full,
        erased,
        state -> ApplicationDecider.decideStartDraft(state, draftStarted("fingerprint")));
  }

  @Test
  void givenDecidedApplicationHistory_whenContentErased_thenStateAndAutoGrantMatchingMatch() {
    List<Object> history =
        List.of(
            created(),
            new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, AT),
            new ApplicationDecisionMadeEvent(
                applicationId,
                2L,
                "GRANTED",
                AutoGrantedState.AUTOGRANTED,
                AT,
                PayloadFingerprint.compute(DECISION_REQUEST),
                Map.of(proceedingId, new ApplicationMeritsDecision("GRANTED", "reason", "why")),
                Map.of("reference", "CERT-1"),
                DECISION_REQUEST,
                "Auto-granted"));

    ApplicationState full = fold(new ApplicationState(), ApplicationEvolve.class, history);
    ApplicationState erased = fold(new ApplicationState(), ApplicationEvolve.class, erase(history));

    assertThat(erased)
        .usingRecursiveComparison()
        .ignoringFields("content", "potentialDuplicates")
        .isEqualTo(full);
    assertThat(erased.getPotentialDuplicates().isErased()).isTrue();
    assertThat(erased.getProceedingDecisions()).containsExactly(Map.entry(proceedingId, "GRANTED"));
    assertThat(full.getContent().isErased()).isFalse();
    assertThat(erased.getContent().isErased()).isTrue();
    assertSameDecision(
        full, erased, state -> ApplicationDecider.isSameAutoGrantRequest(state, DECISION_REQUEST));
    assertSameDecision(
        full, erased, state -> ApplicationDecider.isSameAutoGrantRequest(state, "{\"other\":1}"));
    assertSameDecision(full, erased, state -> decide(state, 2L, proceedingId));
  }

  @Test
  void givenPriorAuthorityHistory_whenContentErased_thenOnlyContentFieldsDifferAndDecisionsMatch() {
    List<Object> submitted =
        List.of(
            started(),
            uploaded(documentId),
            uploaded(deletedDocumentId),
            new PriorAuthorityDocumentTypeUpdatedEvent(
                priorAuthorityId, documentId, Erasable.of("GATEWAY_EVIDENCE"), AT, applicationId),
            new PriorAuthorityDocumentDeletedEvent(
                priorAuthorityId, deletedDocumentId, AT, applicationId),
            new PriorAuthoritySubmittedEvent(priorAuthorityId, applicationId, "EXPERT", 1, 0L, AT),
            new WorkItemAssigned(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                applicationId,
                0L,
                1L,
                caseworkerId,
                AT));

    PriorAuthorityState full =
        fold(new PriorAuthorityState(), PriorAuthorityEvolve.class, submitted);
    PriorAuthorityState erased =
        fold(new PriorAuthorityState(), PriorAuthorityEvolve.class, erase(submitted));

    assertThat(erased.getUploadedDocuments()).hasSize(2);
    assertThat(erased)
        .usingRecursiveComparison()
        .ignoringFields("uploadedDocuments")
        .isEqualTo(full);
    assertThat(erased.getUploadedDocuments())
        .isEqualTo(full.getUploadedDocuments().stream().map(this::withoutContent).toList());

    assertSameDecision(full, erased, state -> decidePriorAuthority(state, caseworkerId, 0L));
    assertSameDecision(full, erased, state -> decidePriorAuthority(state, caseworkerId, 9L));
    assertSameDecision(full, erased, state -> decidePriorAuthority(state, UUID.randomUUID(), 0L));
  }

  @Test
  void givenDecidedPriorAuthorityHistory_whenContentErased_thenRepeatDecisionStillRejected() {
    List<Object> decided =
        List.of(
            started(),
            new PriorAuthoritySubmittedEvent(priorAuthorityId, applicationId, "EXPERT", 1, 0L, AT),
            new WorkItemAssigned(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                applicationId,
                0L,
                1L,
                caseworkerId,
                AT),
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                "EXPERT",
                1L,
                "GRANTED",
                Erasable.of("justified"),
                Erasable.of(new BigDecimal("125.50")),
                Erasable.of(AT),
                AT));

    PriorAuthorityState full = fold(new PriorAuthorityState(), PriorAuthorityEvolve.class, decided);
    PriorAuthorityState erased =
        fold(new PriorAuthorityState(), PriorAuthorityEvolve.class, erase(decided));

    assertThat(erased).usingRecursiveComparison().isEqualTo(full);
    assertSameDecision(full, erased, state -> decidePriorAuthority(state, caseworkerId, 1L));
  }

  private ApplicationCreatedEvent created() {
    return new ApplicationCreatedEvent(
        applicationId,
        "fingerprint",
        "APPLICATION_SUBMITTED",
        1,
        AT,
        List.of(new PotentialDuplicate("LAA-456")),
        "1A234B",
        "LAA-123",
        List.of(proceedingId),
        ParsedAppContentDetails.builder()
            .client(ApplicationClient.builder().firstName("Jane").lastName("Doe").build())
            .proceedings(
                List.of(Proceeding.builder().id(proceedingId).leadProceeding(true).build()))
            .build(),
        "{\"client\":\"Jane Doe\"}");
  }

  private Object decide(ApplicationState state, long expectedVersion, UUID decidedProceedingId) {
    return ApplicationDecider.decideDecision(
        state,
        new MakeApplicationDecisionCommand(
            applicationId,
            caseworkerId,
            expectedVersion,
            "REFUSED",
            List.of(new MakeDecisionProceeding(decidedProceedingId, "REFUSED", "reason", "why")),
            null,
            DECISION_REQUEST,
            "Refused",
            AT));
  }

  private ApplicationDraftStartedEvent draftStarted(String fingerprint) {
    return new ApplicationDraftStartedEvent(
        applicationId,
        1,
        fingerprint,
        AT,
        "1A234B",
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.<String, Object>of("applicant", "Jane Doe"),
        "{\"applicant\":\"Jane Doe\"}",
        List.of(new PotentialDuplicate("LAA-456")));
  }

  private PriorAuthorityDraftStartedEvent started() {
    return new PriorAuthorityDraftStartedEvent(
        priorAuthorityId, applicationId, "EXPERT", 1, AT, "1A234B");
  }

  private PriorAuthorityDocumentUploadedEvent uploaded(UUID id) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        id,
        AT,
        Erasable.of(12L),
        Erasable.of("application/pdf"),
        "checksum",
        applicationId,
        Erasable.of("CIVIL_APPLY"),
        Erasable.of(".pdf"));
  }

  private Object decidePriorAuthority(PriorAuthorityState state, UUID actor, long version) {
    return PriorAuthorityDecider.decideDecision(
        state,
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            actor,
            version,
            "GRANTED",
            "justified",
            new BigDecimal("125.50"),
            null,
            null,
            null,
            AT,
            "{}",
            AT));
  }

  private DocumentMetadata withoutContent(DocumentMetadata document) {
    return new DocumentMetadata(
        document.documentId(),
        null,
        document.uploadedAt(),
        null,
        null,
        document.checksum(),
        null,
        document.deleted(),
        null);
  }

  private List<Object> erase(List<Object> history) {
    return history.stream()
        .map(event -> separator.erase(EventTypeNames.of(event.getClass()), event))
        .toList();
  }

  private static <S> S fold(S state, Class<?> evolve, List<Object> history) {
    for (Object event : history) {
      Method apply =
          Arrays.stream(evolve.getMethods())
              .filter(method -> method.getName().equals("apply"))
              .filter(method -> method.getParameterTypes()[1].equals(event.getClass()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("No evolve for " + event.getClass()));
      try {
        apply.invoke(null, state, event);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new AssertionError(e);
      }
    }
    return state;
  }

  private static <S> void assertSameDecision(S full, S erased, Function<S, Object> decide) {
    assertThat(outcome(() -> decide.apply(erased))).isEqualTo(outcome(() -> decide.apply(full)));
  }

  private static Object outcome(Supplier<Object> decision) {
    try {
      return decision.get();
    } catch (RuntimeException e) {
      return e.getClass().getName() + ": " + e.getMessage();
    }
  }
}
