package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class GetPriorAuthorityUseCaseTest {

  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private QueryGateway queryGateway;

  @InjectMocks private GetPriorAuthorityUseCase useCase;

  @Test
  void givenCounselPriorAuthority_whenRetrieved_thenHydratesStoredDataVersion() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .dataVersion(4L)
            .status("PENDING")
            .build();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            "COUNSEL", "Counsel is required", null, new CounselDetails("TWO_JUNIOR_COUNSEL"), null);
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(priorAuthorityId, 4L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                priorAuthorityId,
                applicationId,
                content,
                "{}",
                Instant.parse("2026-08-26T10:00:00Z")));

    PriorAuthorityResult response = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(response.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.applicationId()).isEqualTo(applicationId);
    assertThat(response.priorAuthorityType()).isEqualTo(PriorAuthorityType.COUNSEL);
    assertThat(response.justification()).isEqualTo("Counsel is required");
    assertThat(response.status()).isEqualTo("PENDING");
    assertThat(response.counselDetails().counselType()).isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    assertThat(response.expertDetails()).isNull();
    assertThat(response.disbursementDetails()).isNull();
    verify(dataStore).get(priorAuthorityId, 4L);
  }

  @ParameterizedTest
  @MethodSource("supportedPriorAuthorityTypes")
  void givenSupportedPriorAuthorityType_whenRetrieved_thenHydratesOnlyMatchingDetails(
      PriorAuthorityContent content,
      PriorAuthorityType expectedType,
      boolean hasExpertDetails,
      boolean hasCounselDetails,
      boolean hasDisbursementDetails) {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .dataVersion(1L)
            .build();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(priorAuthorityId, 1L))
        .thenReturn(
            new PriorAuthorityDataPayload(priorAuthorityId, null, content, "{}", Instant.now()));

    PriorAuthorityResult response = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(result).isSameAs(expected);
  }

  @Test
  void givenExpertCostsWithNullableFields_whenRetrieved_thenHydratesAvailableValues() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .dataVersion(1L)
            .build();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            "EXPERT",
            "Expert is required",
            new ExpertDetails(
                "PSYCHIATRIST",
                "Jane Doe",
                "AB1 2CD",
                new ExpertCosts("FIXED_RATE", null, null, BigDecimal.TEN, false, null)),
            null,
            null);
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(priorAuthorityId, 1L))
        .thenReturn(
            new PriorAuthorityDataPayload(priorAuthorityId, null, content, "{}", Instant.now()));

    PriorAuthorityResult response = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(response.expertDetails().expertCosts().billingType().name()).isEqualTo("FIXED_RATE");
    assertThat(response.expertDetails().expertCosts().hourlyRate()).isNull();
    assertThat(response.expertDetails().expertCosts().timeRequested()).isNull();
    assertThat(response.expertDetails().expertCosts().totalAmount()).isEqualTo(BigDecimal.TEN);
    assertThat(response.expertDetails().expertCosts().apportionment()).isNull();
  }

  @Test
  void givenUnknownPriorAuthority_whenRetrieved_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(priorAuthorityId))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);
  }

  @Test
  void givenInProgressDraft_whenRetrieved_thenFallsBackToDraftStoreWithNullStatus() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            "EXPERT",
            "Expert is required",
            new ExpertDetails("PSYCHIATRIST", "Jane Doe", "AB1 2CD", null),
            null,
            null);
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    content,
                    "{}",
                    Instant.parse("2026-08-26T10:00:00Z"))));

    PriorAuthorityResult result = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(result.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(result.applicationId()).isEqualTo(applicationId);
    assertThat(result.status()).isNull();
    assertThat(result.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
    assertThat(result.expertDetails()).isNotNull();
    assertThat(result.expertDetails().expertFullName()).isEqualTo("Jane Doe");
    verify(dataStore, org.mockito.Mockito.never()).get(any(), anyLong());
  }

  @Test
  void givenReadModelReferencesMissingPayloadVersion_whenRetrieved_thenThrowsConsistencyError() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .dataVersion(2L)
            .build();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class),
            eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(priorAuthorityId, 2L))
        .thenThrow(
            new IllegalStateException(
                "Prior authority data not found for submission "
                    + priorAuthorityId
                    + " version 2"));

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(priorAuthorityId))
        .withMessage(
            "Prior authority data not found for submission " + priorAuthorityId + " version 2");
  }
}
