package uk.gov.justice.laa.dstew.access.command;

import java.sql.SQLException;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Wraps Axon's {@link CommandGateway} with a single retry on concurrent-write failures.
 *
 * <p>Retries once when a {@link ConcurrencyException}, {@link
 * AppendEventsTransactionRejectedException}, or a unique-constraint {@link
 * DataIntegrityViolationException} (SQL state {@code 23505}) is thrown. All other exceptions are
 * propagated immediately.
 */
@Component
public class RetryingCommandDispatcher {

  private final CommandGateway commandGateway;

  public RetryingCommandDispatcher(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Dispatches the command, retrying once on concurrent-write failures. */
  public void dispatch(Object command) {
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
