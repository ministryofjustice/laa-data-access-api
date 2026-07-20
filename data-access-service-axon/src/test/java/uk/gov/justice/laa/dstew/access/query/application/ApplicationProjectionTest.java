package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

class ApplicationProjectionTest {

  private ApplicationReadRepository applicationReadRepository;
  private LinkedApplicationGroupReadRepository groupReadRepository;
  private QueryUpdateEmitter queryUpdateEmitter;
  private ApplicationDataStore applicationDataStore;
  private ApplicationProjection projection;

  @BeforeEach
  void setUp() {
    applicationReadRepository = mock(ApplicationReadRepository.class);
    groupReadRepository = mock(LinkedApplicationGroupReadRepository.class);
    queryUpdateEmitter = mock(QueryUpdateEmitter.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    when(applicationDataStore.get(any(), anyLong()))
        .thenAnswer(
            invocation ->
                ApplicationDataPayload.from(applicationCreationDetails(invocation.getArgument(0))));
    projection =
        new ApplicationProjection(
            applicationReadRepository,
            groupReadRepository,
            queryUpdateEmitter,
            applicationDataStore);
  }

  @Test
  void givenCreatedEvent_whenHandled_thenSavesBeforeEmitting() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationReadModel saved =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.save(any())).thenReturn(saved);

    projection.on(event);

    InOrder order = inOrder(applicationReadRepository, queryUpdateEmitter);
    order.verify(applicationReadRepository).save(any());
    // The default emit(Class, Predicate, U) is called with ApplicationReadModel as U
    order
        .verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenCreatedEvent_whenHandled_thenEmittedPredicateMatchesApplicationId() {
    UUID applicationId = UUID.randomUUID();
    final UUID otherId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);

    final Predicate<?>[] capturedPredicate = new Predicate[1];
    // Stub the default emit(Class, Predicate, U) overload by matching ApplicationReadModel
    org.mockito.Mockito.doAnswer(
            inv -> {
              capturedPredicate[0] = (Predicate<?>) inv.getArgument(1);
              return null;
            })
        .when(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));

    when(applicationReadRepository.save(any()))
        .thenReturn(ApplicationReadModel.builder().applicationId(applicationId).build());

    projection.on(event);

    assertThat(capturedPredicate[0]).isNotNull();
    Predicate<FindApplicationByIdQuery> predicate =
        (Predicate<FindApplicationByIdQuery>) capturedPredicate[0];
    assertThat(predicate.test(new FindApplicationByIdQuery(applicationId))).isTrue();
    assertThat(predicate.test(new FindApplicationByIdQuery(otherId))).isFalse();
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllProjections() {
    projection.reset();

    verify(applicationReadRepository).deleteAllInBatch();
  }

  @Test
  void givenLinkedEvent_whenHandled_thenUpdatesLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationLinkedEvent event =
        new ApplicationLinkedEvent(applicationId, leadApplicationId, Instant.now());

    projection.on(event);

    assertThat(existing.getLeadApplicationId()).isEqualTo(leadApplicationId);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void givenDecisionEvent_whenHandled_thenAdvancesVersionsAndEmitsUpdate() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(
        new ApplicationDecisionMadeEvent(applicationId, 3L, 4L, "GRANTED", false, occurredAt));

    assertThat(existing.getApplicationVersion()).isEqualTo(3L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(4L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));
  }

  @Test
  void givenAssignmentEvent_whenHandled_thenSetsCaseworkerAndVersions() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationAssignedToCaseworkerEvent(applicationId, 1L, 2L, caseworkerId, occurredAt));

    assertThat(existing.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(existing.getApplicationVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(2L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void givenUnassignmentEvent_whenHandled_thenClearsCaseworkerAndAdvancesVersions() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .caseworkerId(UUID.randomUUID())
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 3L, occurredAt));

    assertThat(existing.getCaseworkerId()).isNull();
    assertThat(existing.getApplicationVersion()).isEqualTo(2L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(3L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void
      givenNoteCreatedEvent_whenHandled_thenAdvancesDataVersionWithoutChangingApplicationVersion() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationVersion(0L)
            .applicationDataVersion(0L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent(
            applicationId, 1L, occurredAt));

    assertThat(existing.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationVersion()).isEqualTo(0L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }
}
