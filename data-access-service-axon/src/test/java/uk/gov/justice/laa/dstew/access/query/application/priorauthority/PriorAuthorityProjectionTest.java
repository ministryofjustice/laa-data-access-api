package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityProjectionTest {

  @Mock private PriorAuthorityReadRepository repository;
  @Mock private QueryUpdateEmitter queryUpdateEmitter;

  @InjectMocks private PriorAuthorityProjection projection;

  @Test
  void givenCreatedEvent_whenHandled_thenSavesBeforeEmitting() {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId, UUID.randomUUID(), 1L, "fp", "SUBMITTED", 1, Instant.now());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(event, queryUpdateEmitter);

    InOrder order = inOrder(repository, queryUpdateEmitter);
    order.verify(repository).save(any(PriorAuthorityReadModel.class));
    order
        .verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(PriorAuthorityReadModel.class));
  }

  @Test
  void givenCreatedEvent_whenHandled_thenSavesExactFields() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T10:00:00Z");
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId, applicationId, 1L, "fp", "SUBMITTED", 1, occurredAt);
    PriorAuthorityReadModel[] savedCapture = new PriorAuthorityReadModel[1];
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(event, queryUpdateEmitter);

    assertThat(savedCapture[0].getSubmissionId()).isEqualTo(submissionId);
    assertThat(savedCapture[0].getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCapture[0].getDataVersion()).isEqualTo(1L);
    assertThat(savedCapture[0].getStatus()).isEqualTo("SUBMITTED");
    assertThat(savedCapture[0].getCreatedAt()).isEqualTo(occurredAt);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenCreatedEvent_whenHandled_thenEmittedPredicateMatchesOnlyEventSubmissionId() {
    UUID submissionId = UUID.randomUUID();
    final UUID otherId = UUID.randomUUID();
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId, UUID.randomUUID(), 1L, "fp", "SUBMITTED", 1, Instant.now());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Predicate<?>[] capturedPredicate = new Predicate[1];
    doAnswer(
            inv -> {
              capturedPredicate[0] = (Predicate<?>) inv.getArgument(1);
              return null;
            })
        .when(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(PriorAuthorityReadModel.class));

    projection.on(event, queryUpdateEmitter);

    assertThat(capturedPredicate[0]).isNotNull();
    Predicate<FindPriorAuthorityBySubmissionIdQuery> predicate =
        (Predicate<FindPriorAuthorityBySubmissionIdQuery>) capturedPredicate[0];
    assertThat(predicate.test(new FindPriorAuthorityBySubmissionIdQuery(submissionId))).isTrue();
    assertThat(predicate.test(new FindPriorAuthorityBySubmissionIdQuery(otherId))).isFalse();
  }

  @Test
  void givenSubmissionId_whenQueryHandled_thenReturnsPresentWhenFound() {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityReadModel model =
        PriorAuthorityReadModel.builder().submissionId(submissionId).build();
    when(repository.findById(submissionId)).thenReturn(Optional.of(model));

    Optional<PriorAuthorityReadModel> result =
        projection.handle(new FindPriorAuthorityBySubmissionIdQuery(submissionId));

    assertThat(result).isPresent().contains(model);
  }

  @Test
  void givenMissingSubmissionId_whenQueryHandled_thenReturnsEmpty() {
    UUID submissionId = UUID.randomUUID();
    when(repository.findById(submissionId)).thenReturn(Optional.empty());

    Optional<PriorAuthorityReadModel> result =
        projection.handle(new FindPriorAuthorityBySubmissionIdQuery(submissionId));

    assertThat(result).isEmpty();
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllInBatch() {
    projection.reset();

    verify(repository).deleteAllInBatch();
  }

  @Test
  void givenSubmittedEvent_whenHandled_thenCreatesRowAndEmits() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T10:00:00Z");
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(submissionId, applicationId, 0L, "PENDING", occurredAt);

    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(event, queryUpdateEmitter);

    InOrder order = inOrder(repository, queryUpdateEmitter);
    order.verify(repository).save(any(PriorAuthorityReadModel.class));
    order
        .verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(PriorAuthorityReadModel.class));
  }

  @Test
  void givenSubmittedEvent_whenHandled_thenSavesExactFields() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T10:00:00Z");
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(submissionId, applicationId, 0L, "PENDING", occurredAt);
    PriorAuthorityReadModel[] savedCapture = new PriorAuthorityReadModel[1];
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(event, queryUpdateEmitter);

    assertThat(savedCapture[0].getSubmissionId()).isEqualTo(submissionId);
    assertThat(savedCapture[0].getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCapture[0].getDataVersion()).isEqualTo(0L);
    assertThat(savedCapture[0].getStatus()).isEqualTo("PENDING");
    assertThat(savedCapture[0].getCreatedAt()).isEqualTo(occurredAt);
  }
}
