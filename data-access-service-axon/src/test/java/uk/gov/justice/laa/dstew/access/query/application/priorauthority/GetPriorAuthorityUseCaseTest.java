package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class GetPriorAuthorityUseCaseTest {

  @Mock private QueryGateway queryGateway;

  @InjectMocks private GetPriorAuthorityUseCase useCase;

  @Test
  void givenProjectedPriorAuthority_whenRetrieved_thenReturnsProjectionResult() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityResult projectedResult =
        new PriorAuthorityResult(
            priorAuthorityId,
            applicationId,
            "Counsel is required",
            "PENDING",
            PriorAuthorityType.COUNSEL,
            null,
            new uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails(
                uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType
                    .TWO_JUNIOR_COUNSEL),
            null);
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(projectedResult));

    PriorAuthorityResult response = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(response).isSameAs(projectedResult);
    verify(queryGateway)
        .query(
            eq(new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId)),
            eq(PriorAuthorityResult.class));
  }

  @Test
  void givenUnknownPriorAuthority_whenRetrieved_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(priorAuthorityId))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);
  }
}
