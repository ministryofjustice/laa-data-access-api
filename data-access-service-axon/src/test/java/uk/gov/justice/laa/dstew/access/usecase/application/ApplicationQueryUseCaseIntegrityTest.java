package uk.gov.justice.laa.dstew.access.usecase.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryIntegrityException;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryUseCaseIntegrityTest {

  @Mock private QueryGateway queryGateway;

  @InjectMocks private ApplicationQueryUseCase useCase;

  @ParameterizedTest(name = "{0} CompletionException wrapper(s)")
  @ValueSource(ints = {1, 2})
  void givenQueryFutureWrapsIntegrityException_whenGetHistory_thenRethrowsIntegrityException(
      int wrapperDepth) {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var integrityException =
        new ApplicationHistoryIntegrityException(
            applicationId, submissionId, "conflicting priorAuthorityType values");
    Throwable wrappedFailure = integrityException;
    for (int wrapperIndex = 0; wrapperIndex < wrapperDepth; wrapperIndex++) {
      wrappedFailure = new CompletionException(wrappedFailure);
    }
    var wrappedFuture = CompletableFuture.<ApplicationHistoryResult>failedFuture(wrappedFailure);
    when(queryGateway.query(any(), eq(ApplicationHistoryResult.class))).thenReturn(wrappedFuture);

    assertThatThrownBy(() -> useCase.getApplicationHistory(applicationId, List.of()))
        .isInstanceOf(ApplicationHistoryIntegrityException.class)
        .isSameAs(integrityException);
  }

  @Test
  void givenQueryFutureWrapsOtherException_whenGetHistory_thenRethrowsCompletionException() {
    UUID applicationId = UUID.randomUUID();
    var unrelatedException = new RuntimeException("unrelated failure");
    var wrappedFuture =
        CompletableFuture.<ApplicationHistoryResult>failedFuture(
            new CompletionException(unrelatedException));
    when(queryGateway.query(any(), eq(ApplicationHistoryResult.class))).thenReturn(wrappedFuture);

    assertThatThrownBy(() -> useCase.getApplicationHistory(applicationId, List.of()))
        .isInstanceOf(CompletionException.class)
        .hasCause(unrelatedException);
  }
}
