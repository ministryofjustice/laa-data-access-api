package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.util.List;
import java.util.function.BiFunction;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateSynchronousApplicationCommand;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Validates create-application content before Axon resolves a command handler. */
@Component
public class CreateApplicationSchemaValidationDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage<?>> {

  private final JsonSchemaValidator jsonSchemaValidator;

  public CreateApplicationSchemaValidationDispatchInterceptor(
      JsonSchemaValidator jsonSchemaValidator) {
    this.jsonSchemaValidator = jsonSchemaValidator;
  }

  @Override
  public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
      List<? extends CommandMessage<?>> messages) {
    return (index, commandMessage) -> {
      if (commandMessage.getPayload() instanceof CreateSynchronousApplicationCommand command) {
        jsonSchemaValidator.validate(
            command.applicationContent(), command.schemaName(), command.schemaVersion());
      }
      return commandMessage;
    };
  }
}
