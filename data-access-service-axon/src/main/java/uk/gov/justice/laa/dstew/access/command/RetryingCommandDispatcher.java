package uk.gov.justice.laa.dstew.access.command;

import java.sql.SQLException;
import java.util.function.Supplier;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Wraps Axon's {@link CommandGateway} with a single retry on concurrent-write failures.
 *
 * <p>Retries once when a {@link ConcurrencyException}, {@link
 * AppendEventsTransactionRejectedException}, {@link OptimisticLockingFailureException}, or a
 * unique-constraint {@link DataIntegrityViolationException} (SQL state {@code 23505}) is thrown.
 * The first three failures are also recognised when nested in a runtime exception.
 * Unique-constraint failures require a top-level {@code DataIntegrityViolationException}. All other
 * exceptions are propagated immediately.
 */
@Component
public class RetryingCommandDispatcher {

  private static final String UNIQUE_CONSTRAINT_VIOLATION = "23505";

  private final CommandGateway commandGateway;

  public RetryingCommandDispatcher(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Dispatches the command, retrying once on concurrent-write failures. */
  public void dispatch(Object command) {
    dispatch(() -> commandGateway.sendAndWait(command));
  }

  /**
   * Dispatches the command and returns its response, retrying once on concurrent-write failures.
   */
  public <R> R dispatch(Object command, Class<R> responseType) {
    return dispatch(() -> commandGateway.sendAndWait(command, responseType));
  }

  /**
   * Executes one retryable operation, allowing callers to scope a complete attempt (including
   * transaction setup) inside the retry boundary.
   */
  public <R> R dispatch(Supplier<R> dispatch) {
    try {
      return dispatch.get();
    } catch (RuntimeException first) {
      if (!isRetryableConcurrentWrite(first)) {
        throw first;
      }
      try {
        return dispatch.get();
      } catch (RuntimeException retry) {
        retry.addSuppressed(first);
        throw retry;
      }
    }
  }

  private boolean isRetryableConcurrentWrite(RuntimeException exception) {
    return hasCause(exception, ConcurrencyException.class)
        || hasCause(exception, AppendEventsTransactionRejectedException.class)
        || hasCause(exception, OptimisticLockingFailureException.class)
        || exception instanceof DataIntegrityViolationException
            && hasUniqueConstraintViolation(exception);
  }

  private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
    Throwable cause = exception;
    while (cause != null) {
      if (causeType.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private boolean hasUniqueConstraintViolation(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SQLException sqlException
          && UNIQUE_CONSTRAINT_VIOLATION.equals(sqlException.getSQLState())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
