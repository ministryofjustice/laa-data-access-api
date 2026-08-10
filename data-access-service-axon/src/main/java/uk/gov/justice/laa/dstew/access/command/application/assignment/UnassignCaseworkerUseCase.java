package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.sql.SQLException;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Dispatches an unassign-caseworker command with a single retry on concurrent-write failures. */
@Component
public class UnassignCaseworkerUseCase {

  private final CommandGateway commandGateway;

  public UnassignCaseworkerUseCase(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Dispatches the command to the application aggregate. */
  public void execute(UnassignCaseworkerFromApplicationCommand command) {
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
