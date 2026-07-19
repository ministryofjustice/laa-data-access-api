package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Validates and dispatches a single Application assignment command. */
@Service
public class AssignCaseworkerService {

  private final CaseworkerRepository caseworkerRepository;
  private final EventStore eventStore;
  private final CommandGateway commandGateway;

  /** Creates the assignment coordinator with its directory, event store, and command gateway. */
  public AssignCaseworkerService(
      CaseworkerRepository caseworkerRepository,
      EventStore eventStore,
      CommandGateway commandGateway) {
    this.caseworkerRepository = caseworkerRepository;
    this.eventStore = eventStore;
    this.commandGateway = commandGateway;
  }

  /** Validates and assigns the caseworker to one Application. */
  @Transactional
  public void assign(
      UUID caseworkerId, UUID applicationId, String serialisedRequest, String eventDescription) {
    if (!caseworkerRepository.existsById(caseworkerId)) {
      throw new ResourceNotFoundException("No caseworker found with id: " + caseworkerId);
    }
    if (!eventStore.readEvents(applicationId.toString()).hasNext()) {
      throw new ResourceNotFoundException("No application found with id: " + applicationId);
    }

    Instant occurredAt = Instant.now();
    commandGateway.sendAndWait(
        new AssignCaseworkerToApplicationCommand(
            applicationId, caseworkerId, serialisedRequest, eventDescription, occurredAt));
  }
}
