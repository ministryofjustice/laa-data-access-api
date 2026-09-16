package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;

@ExtendWith(MockitoExtension.class)
class UpdatePriorAuthorityDocumentTypeUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;

  @Test
  void givenDocumentTypeRequest_whenExecute_thenSerialisesAndDispatchesUpdateCommand() {
    UpdatePriorAuthorityDocumentTypeUseCase useCase =
        new UpdatePriorAuthorityDocumentTypeUseCase(dispatcher, new ObjectMapper());
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UpdatePriorAuthorityDocumentTypeRequest request =
        new UpdatePriorAuthorityDocumentTypeRequest(PriorAuthorityDocumentType.GATEWAY_EVIDENCE);

    UpdatePriorAuthorityDocumentTypeResult result =
        useCase.execute(priorAuthorityId, documentId, request);

    ArgumentCaptor<PriorAuthorityDocumentTypeUpdateCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentTypeUpdateCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    PriorAuthorityDocumentTypeUpdateCommand command = commandCaptor.getValue();
    assertThat(command.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(command.documentId()).isEqualTo(documentId);
    assertThat(command.documentType()).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(command.serialisedRequest()).contains("GATEWAY_EVIDENCE");
    assertThat(command.occurredAt()).isEqualTo(result.updatedAt());
    assertThat(result.documentId()).isEqualTo(documentId);
  }
}
