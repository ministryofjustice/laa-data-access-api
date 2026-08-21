package uk.gov.justice.laa.dstew.access.config.interceptor;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Validates command content against its schema before Axon resolves a command handler. */
@Component
public class ContentSchemaValidationDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage> {

  private final JsonSchemaValidator jsonSchemaValidator;

  public ContentSchemaValidationDispatchInterceptor(JsonSchemaValidator jsonSchemaValidator) {
    this.jsonSchemaValidator = jsonSchemaValidator;
  }

  @Override
  public MessageStream<?> interceptOnDispatch(
      CommandMessage message,
      ProcessingContext context,
      MessageDispatchInterceptorChain<CommandMessage> chain) {

    if (message.payload() instanceof CreateApplicationCommand command) {
      jsonSchemaValidator.validate(
          command.applicationContent(), command.schemaName(), command.schemaVersion());
    }

    if (message.payload() instanceof CreatePriorAuthorityCommand command) {
      jsonSchemaValidator.validate(
          command.content(), command.schemaName(), command.schemaVersion());
    }

    return chain.proceed(message, context);
  }
}
