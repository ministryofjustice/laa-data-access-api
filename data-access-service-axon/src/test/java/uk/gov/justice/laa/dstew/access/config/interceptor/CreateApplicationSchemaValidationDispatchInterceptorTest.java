package uk.gov.justice.laa.dstew.access.config.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

class CreateApplicationSchemaValidationDispatchInterceptorTest {

  @Test
  void givenCcsCreateCommand_whenDispatched_thenValidatesWithCommandSchema() {
    JsonSchemaValidator validator = mock(JsonSchemaValidator.class);
    CreateApplicationSchemaValidationDispatchInterceptor interceptor =
        new CreateApplicationSchemaValidationDispatchInterceptor(validator);
    UUID id = UUID.randomUUID();
    CreateApplicationCommand command = createCommand(id, "CssApplication.json", "CCS");
    var commandMessage =
        new GenericCommandMessage(new MessageType(CreateApplicationCommand.class), command);
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(commandMessage, null, chain);

    verify(validator).validate(command.applicationContent(), "CssApplication.json", 1);
    verify(chain).proceed(commandMessage, null);
  }

  @Test
  void givenNonCreateCommand_whenDispatched_thenSkipsSchemaValidation() {
    JsonSchemaValidator validator = mock(JsonSchemaValidator.class);
    CreateApplicationSchemaValidationDispatchInterceptor interceptor =
        new CreateApplicationSchemaValidationDispatchInterceptor(validator);
    var commandMessage =
        new GenericCommandMessage(new MessageType(String.class), "not a create command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(commandMessage, null, chain);

    verifyNoInteractions(validator);
    verify(chain).proceed(commandMessage, null);
  }

  @SuppressWarnings("unchecked")
  private MessageDispatchInterceptorChain<CommandMessage> chain() {
    MessageDispatchInterceptorChain<CommandMessage> chain =
        mock(MessageDispatchInterceptorChain.class);
    when(chain.proceed(any(), isNull())).thenReturn(mock(MessageStream.class));
    return chain;
  }

  private CreateApplicationCommand createCommand(
      UUID id, String schemaName, String applicationType) {
    return new CreateApplicationCommand(
        id,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", id.toString()),
        List.of(),
        "{}",
        1,
        schemaName,
        applicationType);
  }
}
