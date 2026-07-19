package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Validates and dispatches an Application caseworker-unassignment command. */
@Service
public class UnassignCaseworkerService {

  private final EventStore eventStore;
  private final CommandGateway commandGateway;

  /** Creates the unassignment coordinator with its event store and command gateway. */
  public UnassignCaseworkerService(EventStore eventStore, CommandGateway commandGateway) {
    this.eventStore = eventStore;
    this.commandGateway = commandGateway;
  }

  /** Validates the Application and removes its current caseworker assignment. */
  @Transactional
  public void unassign(UUID applicationId, String serialisedRequest, String eventDescription) {
    if (!eventStore.readEvents(applicationId.toString()).hasNext()) {
      throw new ResourceNotFoundException("No application found with id: " + applicationId);
    }
    commandGateway.sendAndWait(
        new UnassignCaseworkerFromApplicationCommand(
            applicationId, serialisedRequest, eventDescription, Instant.now()));
  }
}
