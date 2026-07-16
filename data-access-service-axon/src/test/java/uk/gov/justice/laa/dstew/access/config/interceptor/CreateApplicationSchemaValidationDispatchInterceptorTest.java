package uk.gov.justice.laa.dstew.access.config.interceptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateSynchronousApplicationCommand;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

class CreateApplicationSchemaValidationDispatchInterceptorTest {

  @Test
  void givenCcsCreateCommand_whenDispatched_thenValidatesWithCommandSchema() {
    JsonSchemaValidator validator = mock(JsonSchemaValidator.class);
    CreateApplicationSchemaValidationDispatchInterceptor interceptor =
        new CreateApplicationSchemaValidationDispatchInterceptor(validator);
    CreateSynchronousApplicationCommand command = createCommand("CssApplication.json", "CCS");
    var commandMessage = GenericCommandMessage.asCommandMessage(command);

    interceptor.handle(List.of(commandMessage)).apply(0, commandMessage);

    verify(validator).validate(command.applicationContent(), "CssApplication.json", 1);
  }

  @Test
  void givenNonCreateCommand_whenDispatched_thenSkipsSchemaValidation() {
    JsonSchemaValidator validator = mock(JsonSchemaValidator.class);
    CreateApplicationSchemaValidationDispatchInterceptor interceptor =
        new CreateApplicationSchemaValidationDispatchInterceptor(validator);
    var commandMessage = GenericCommandMessage.asCommandMessage("not a create command");

    interceptor.handle(List.of(commandMessage)).apply(0, commandMessage);

    verifyNoInteractions(validator);
  }

  private CreateSynchronousApplicationCommand createCommand(
      String schemaName, String applicationType) {
    return new CreateSynchronousApplicationCommand(
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", UUID.randomUUID().toString()),
        List.of(),
        "{}",
        1,
        schemaName,
        applicationType);
  }
}
