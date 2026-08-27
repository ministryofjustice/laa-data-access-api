package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;

class GetPriorAuthorityUseCaseTest {

  private PriorAuthorityDataStore dataStore;
  private QueryGateway queryGateway;
  private GetPriorAuthorityUseCase useCase;

  @BeforeEach
  void setUp() {
    dataStore = org.mockito.Mockito.mock(PriorAuthorityDataStore.class);
    queryGateway = org.mockito.Mockito.mock(QueryGateway.class);
    useCase = new GetPriorAuthorityUseCase(dataStore, queryGateway);
  }

  @Test
  void givenCounselPriorAuthority_whenRetrieved_thenHydratesStoredDataVersion() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder()
            .submissionId(submissionId)
            .applicationId(applicationId)
            .dataVersion(4L)
            .status("PENDING")
            .build();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            "COUNSEL", "Counsel is required", null, new CounselDetails("TWO_JUNIOR_COUNSEL"), null);
    when(queryGateway.query(
            any(FindPriorAuthorityBySubmissionIdQuery.class), eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(submissionId, 4L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                submissionId, applicationId, content, "{}", Instant.parse("2026-08-26T10:00:00Z")));

    PriorAuthorityResponse response = useCase.getPriorAuthority(submissionId);

    assertThat(response.getPriorAuthorityId()).isEqualTo(submissionId);
    assertThat(response.getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getPriorAuthorityType())
        .isEqualTo(PriorAuthorityResponse.PriorAuthorityTypeEnum.COUNSEL);
    assertThat(response.getJustification()).isEqualTo("Counsel is required");
    assertThat(response.getStatus()).isEqualTo("PENDING");
    assertThat(response.getCounselDetails().getCounselType().getValue())
        .isEqualTo("TWO_JUNIOR_COUNSEL");
    assertThat(response.getExpertDetails()).isNull();
    assertThat(response.getDisbursementDetails()).isNull();
    verify(dataStore).get(submissionId, 4L);
  }

  @Test
  void givenUnknownPriorAuthority_whenRetrieved_thenThrowsNotFound() {
    UUID submissionId = UUID.randomUUID();
    when(queryGateway.query(
            any(FindPriorAuthorityBySubmissionIdQuery.class), eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(submissionId))
        .withMessage("No prior authority found with ID: " + submissionId);
  }

  @Test
  void givenReadModelReferencesMissingPayloadVersion_whenRetrieved_thenThrowsConsistencyError() {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityReadModel readModel =
        PriorAuthorityReadModel.builder().submissionId(submissionId).dataVersion(2L).build();
    when(queryGateway.query(
            any(FindPriorAuthorityBySubmissionIdQuery.class), eq(PriorAuthorityReadModel.class)))
        .thenReturn(CompletableFuture.completedFuture(readModel));
    when(dataStore.get(submissionId, 2L))
        .thenThrow(
            new IllegalStateException(
                "Prior authority data not found for submission " + submissionId + " version 2"));

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(submissionId))
        .withMessage(
            "Prior authority data not found for submission " + submissionId + " version 2");
  }
}
