package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Validates and dispatches a single Application assignment command. */
@Service
public class AssignCaseworkerUseCase {

  private final CaseworkerRepository caseworkerRepository;
  private final RetryingCommandDispatcher dispatcher;

  /** Creates the assignment coordinator with its directory and command gateway. */
  public AssignCaseworkerUseCase(
      CaseworkerRepository caseworkerRepository, RetryingCommandDispatcher dispatcher) {
    this.caseworkerRepository = caseworkerRepository;
    this.dispatcher = dispatcher;
  }

  /** Validates and assigns the caseworker to one Application. */
  @Transactional
  public CompletableFuture<Void> assign(
      UUID caseworkerId, UUID applicationId, String serialisedRequest, String eventDescription) {
    if (!caseworkerRepository.existsById(caseworkerId)) {
      throw new ResourceNotFoundException("No caseworker found with id: " + caseworkerId);
    }
    Instant occurredAt = Instant.now();
    return dispatcher
        .dispatchAsync(
            new AssignCaseworkerToApplicationCommand(
                applicationId, caseworkerId, serialisedRequest, eventDescription, occurredAt))
        .thenApply(ignored -> null);
  }
}
