package uk.gov.justice.laa.dstew.access.command.application;

import java.sql.SQLException;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

/**
 * Dispatches a create-application command and waits for the projection to confirm the application
 * is readable.
 */
@Component
public class CreateApplicationUseCase {

  private final CommandGateway commandGateway;
  private final SubscriptionProjectionGateway projectionGateway;

  public CreateApplicationUseCase(
      CommandGateway commandGateway, SubscriptionProjectionGateway projectionGateway) {
    this.commandGateway = commandGateway;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the command and waits for the projection to become readable.
   *
   * @return {@code true} when the projection confirms the application within the configured
   *     timeout; {@code false} on timeout — the command has still committed.
   */
  public boolean execute(CreateApplicationCommand command) {
    return projectionGateway.awaitProjection(
        new FindApplicationByIdQuery(command.applicationId()),
        ApplicationReadModel.class,
        () -> dispatchWithRetry(command));
  }

  private void dispatchWithRetry(Object command) {
    try {
      commandGateway.sendAndWait(command);
    } catch (RuntimeException first) {
      if (!isRetryableConcurrentWrite(first)) {
        throw first;
      }
      try {
        commandGateway.sendAndWait(command);
      } catch (RuntimeException retry) {
        retry.addSuppressed(first);
        throw retry;
      }
    }
  }

  private boolean isRetryableConcurrentWrite(RuntimeException exception) {
    return exception instanceof ConcurrencyException
        || exception instanceof AppendEventsTransactionRejectedException
        || exception instanceof DataIntegrityViolationException
            && hasUniqueConstraintViolation(exception);
  }

  private boolean hasUniqueConstraintViolation(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SQLException sqlException
          && "23505".equals(sqlException.getSQLState())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
