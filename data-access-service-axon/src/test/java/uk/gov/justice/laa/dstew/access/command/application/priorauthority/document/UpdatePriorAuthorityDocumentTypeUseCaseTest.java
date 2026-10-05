package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;

@ExtendWith(MockitoExtension.class)
class UpdatePriorAuthorityDocumentTypeUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;

  @Test
  void givenDocumentType_whenExecute_thenDispatchesUpdateCommand() {
    UpdatePriorAuthorityDocumentTypeUseCase useCase =
        new UpdatePriorAuthorityDocumentTypeUseCase(dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    String serialisedRequest = "{\"documentType\":\"GATEWAY_EVIDENCE\"}";

    UpdatePriorAuthorityDocumentTypeResult result =
        useCase.execute(priorAuthorityId, documentId, "GATEWAY_EVIDENCE", serialisedRequest);

    ArgumentCaptor<PriorAuthorityDocumentTypeUpdateCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentTypeUpdateCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    PriorAuthorityDocumentTypeUpdateCommand command = commandCaptor.getValue();
    assertThat(command.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(command.documentId()).isEqualTo(documentId);
    assertThat(command.documentType()).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(command.serialisedRequest()).isEqualTo(serialisedRequest);
    assertThat(command.occurredAt()).isEqualTo(result.updatedAt());
    assertThat(result.documentId()).isEqualTo(documentId);
  }
}
