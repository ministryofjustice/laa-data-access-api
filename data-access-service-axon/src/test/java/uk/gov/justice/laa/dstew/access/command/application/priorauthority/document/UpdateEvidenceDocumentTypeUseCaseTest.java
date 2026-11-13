package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

@ExtendWith(MockitoExtension.class)
class UpdateEvidenceDocumentTypeUseCaseTest {

  private static final AuthenticatedCaller CALLER =
      new AuthenticatedCaller(Optional.empty(), Set.of(), Set.of("1A001B"), Set.of());

  @Mock private RetryingCommandDispatcher dispatcher;

  @Test
  void givenDocumentType_whenExecute_thenDispatchesUpdateCommand() {
    UpdatePriorAuthorityDocumentTypeUseCase useCase =
        new UpdatePriorAuthorityDocumentTypeUseCase(dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    String serialisedRequest = "{\"documentType\":\"GATEWAY_EVIDENCE\"}";

    UpdatePriorAuthorityDocumentTypeResult result =
        useCase.execute(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", serialisedRequest, CALLER);

    ArgumentCaptor<PriorAuthorityDocumentTypeUpdateCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentTypeUpdateCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    PriorAuthorityDocumentTypeUpdateCommand command = commandCaptor.getValue();
    assertThat(command.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(command.documentId()).isEqualTo(documentId);
    assertThat(command.documentType()).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(command.serialisedRequest()).isEqualTo(serialisedRequest);
    assertThat(command.caller()).isEqualTo(CALLER);
    assertThat(command.occurredAt()).isEqualTo(result.updatedAt());
    assertThat(result.documentId()).isEqualTo(documentId);
  }
}
