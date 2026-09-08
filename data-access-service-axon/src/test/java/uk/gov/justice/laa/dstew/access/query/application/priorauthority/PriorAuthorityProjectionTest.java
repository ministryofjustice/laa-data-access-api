package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment;
import uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityProjectionTest {

  @Mock private PriorAuthorityReadRepository repository;
  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private QueryUpdateEmitter queryUpdateEmitter;
  @InjectMocks private PriorAuthorityProjection projection;

  @Test
  @SuppressWarnings("unchecked")
  void givenCreatedEvent_whenHandled_thenSavesBeforeEmitting() {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId, UUID.randomUUID(), "EXPERT", 1L, "fp", "SUBMITTED", 1, Instant.now());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(event, queryUpdateEmitter);

    InOrder order = inOrder(repository, queryUpdateEmitter);
    order.verify(repository).save(any(PriorAuthorityReadModel.class));
    QueryUpdateEmitter verifiedEmitter = order.verify(queryUpdateEmitter);
    verifiedEmitter.emit(any(Class.class), any(Predicate.class), any(Boolean.class));
  }

  @Test
  void givenCreatedEvent_whenHandled_thenSavesExactFields() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T10:00:00Z");
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId, applicationId, "EXPERT", 1L, "fp", "SUBMITTED", 1, occurredAt);
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
            submissionId, UUID.randomUUID(), "EXPERT", 1L, "fp", "SUBMITTED", 1, Instant.now());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Predicate<?>[] capturedPredicate = new Predicate[1];
    doAnswer(
            inv -> {
              capturedPredicate[0] = (Predicate<?>) inv.getArgument(1);
              return null;
            })
        .when(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(Boolean.class));

    projection.on(event, queryUpdateEmitter);

    assertThat(capturedPredicate[0]).isNotNull();
    Predicate<PriorAuthorityExistsBySubmissionIdQuery> predicate =
        (Predicate<PriorAuthorityExistsBySubmissionIdQuery>) capturedPredicate[0];
    assertThat(predicate.test(new PriorAuthorityExistsBySubmissionIdQuery(submissionId))).isTrue();
    assertThat(predicate.test(new PriorAuthorityExistsBySubmissionIdQuery(otherId))).isFalse();
  }

  @Test
  void givenSubmissionId_whenQueryHandled_thenReturnsHydratedResult() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityReadModel model =
        PriorAuthorityReadModel.builder()
            .submissionId(submissionId)
            .applicationId(applicationId)
            .dataVersion(4L)
            .status("PENDING")
            .build();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            COUNSEL,
            "Counsel is required",
            null,
            new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
            null);
    when(repository.findById(submissionId)).thenReturn(Optional.of(model));
    when(dataStore.get(submissionId, 4L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                submissionId, applicationId, content, "{}", Instant.now()));

    PriorAuthorityResult result =
        projection.handle(new FindPriorAuthorityBySubmissionIdQuery(submissionId));

    assertThat(result.priorAuthorityId()).isEqualTo(submissionId);
    assertThat(result.applicationId()).isEqualTo(applicationId);
    assertThat(result.priorAuthorityType()).isEqualTo(COUNSEL);
    assertThat(result.justification()).isEqualTo("Counsel is required");
    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.counselDetails().counselType()).isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    assertThat(result.expertDetails()).isNull();
    assertThat(result.disbursementDetails()).isNull();
  }

  @Test
  void givenExpertDetails_whenQueryHandled_thenHydratesOnlyExpertDetails() {
    ExpertCosts expertCosts =
        new ExpertCosts(
            BillingType.HOURLY,
            new BigDecimal("125.50"),
            new TimeRequested(2, 30),
            new BigDecimal("313.75"),
            true,
            new Apportionment(3, new BigDecimal("104.58")));
    PriorAuthorityResult expertResult =
        handleContent(
            new PriorAuthorityContent(
                EXPERT,
                "Expert required",
                new ExpertDetails("Accountant", "Ada Lovelace", "SW1A 1AA", expertCosts),
                null,
                null));

    assertThat(expertResult.priorAuthorityType()).isEqualTo(EXPERT);
    assertThat(expertResult.justification()).isEqualTo("Expert required");
    assertThat(expertResult.expertDetails().expertType()).isEqualTo("Accountant");
    assertThat(expertResult.expertDetails().expertFullName()).isEqualTo("Ada Lovelace");
    assertThat(expertResult.expertDetails().expertPostcode()).isEqualTo("SW1A 1AA");
    assertThat(expertResult.expertDetails().expertCosts().billingType())
        .isEqualTo(BillingType.HOURLY);
    assertThat(expertResult.expertDetails().expertCosts().hourlyRate())
        .isEqualTo(new BigDecimal("125.50"));
    assertThat(expertResult.expertDetails().expertCosts().timeRequested().hours()).isEqualTo(2);
    assertThat(expertResult.expertDetails().expertCosts().timeRequested().minutes()).isEqualTo(30);
    assertThat(expertResult.expertDetails().expertCosts().totalAmount())
        .isEqualTo(new BigDecimal("313.75"));
    assertThat(expertResult.expertDetails().expertCosts().costsSharedWithOtherParties()).isTrue();
    assertThat(expertResult.expertDetails().expertCosts().apportionment().partiesSharingCosts())
        .isEqualTo(3);
    assertThat(expertResult.expertDetails().expertCosts().apportionment().clientShareAmount())
        .isEqualTo(new BigDecimal("104.58"));
    assertThat(expertResult.counselDetails()).isNull();
    assertThat(expertResult.disbursementDetails()).isNull();
  }

  @Test
  void givenCounselDetails_whenQueryHandled_thenHydratesOnlyCounselDetails() {
    PriorAuthorityResult counselResult =
        handleContent(
            new PriorAuthorityContent(
                COUNSEL,
                "Counsel required",
                null,
                new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
                null));

    assertThat(counselResult.priorAuthorityType()).isEqualTo(COUNSEL);
    assertThat(counselResult.justification()).isEqualTo("Counsel required");
    assertThat(counselResult.counselDetails().counselType())
        .isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    assertThat(counselResult.expertDetails()).isNull();
    assertThat(counselResult.disbursementDetails()).isNull();
  }

  @Test
  void givenDisbursementDetails_whenQueryHandled_thenHydratesOnlyDisbursementDetails() {
    PriorAuthorityResult disbursementResult =
        handleContent(
            new PriorAuthorityContent(
                DISBURSEMENT,
                "Disbursement required",
                null,
                null,
                new DisbursementDetails("Travel", BigDecimal.TEN)));

    assertThat(disbursementResult.priorAuthorityType()).isEqualTo(DISBURSEMENT);
    assertThat(disbursementResult.justification()).isEqualTo("Disbursement required");
    assertThat(disbursementResult.disbursementDetails().disbursementPurpose()).isEqualTo("Travel");
    assertThat(disbursementResult.disbursementDetails().disbursementAmount())
        .isEqualTo(BigDecimal.TEN);
    assertThat(disbursementResult.expertDetails()).isNull();
    assertThat(disbursementResult.counselDetails()).isNull();
  }

  @ParameterizedTest
  @EnumSource(PriorAuthorityType.class)
  void givenTypeWithAbsentDetails_whenQueryHandled_thenHydratedDetailsAreNull(
      PriorAuthorityType type) {
    PriorAuthorityResult result =
        handleContent(new PriorAuthorityContent(type, "Required", null, null, null));

    assertThat(result.expertDetails()).isNull();
    assertThat(result.counselDetails()).isNull();
    assertThat(result.disbursementDetails()).isNull();
  }

  @Test
  void givenNullType_whenQueryHandled_thenPriorAuthorityTypeIsNull() {
    PriorAuthorityResult result =
        handleContent(new PriorAuthorityContent(null, "Required", null, null, null));

    assertThat(result.priorAuthorityType()).isNull();
  }

  @Test
  void givenMissingSubmissionId_whenQueryHandled_thenReturnsNull() {
    UUID submissionId = UUID.randomUUID();
    when(repository.findById(submissionId)).thenReturn(Optional.empty());

    assertThat(projection.handle(new FindPriorAuthorityBySubmissionIdQuery(submissionId))).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenSubmissionId_whenExistsQueryHandled_thenReturnsRepositoryResult(boolean exists) {
    UUID submissionId = UUID.randomUUID();
    when(repository.existsById(submissionId)).thenReturn(exists);

    boolean result = projection.handle(new PriorAuthorityExistsBySubmissionIdQuery(submissionId));

    assertThat(result).isEqualTo(exists);
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllInBatch() {
    projection.reset();

    verify(repository).deleteAllInBatch();
  }

  private PriorAuthorityResult handleContent(PriorAuthorityContent content) {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityReadModel model =
        PriorAuthorityReadModel.builder()
            .submissionId(submissionId)
            .applicationId(UUID.randomUUID())
            .dataVersion(1L)
            .status("PENDING")
            .build();
    when(repository.findById(submissionId)).thenReturn(Optional.of(model));
    when(dataStore.get(submissionId, 1L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                submissionId, model.getApplicationId(), content, "{}", Instant.now()));

    return projection.handle(new FindPriorAuthorityBySubmissionIdQuery(submissionId));
  }
}
