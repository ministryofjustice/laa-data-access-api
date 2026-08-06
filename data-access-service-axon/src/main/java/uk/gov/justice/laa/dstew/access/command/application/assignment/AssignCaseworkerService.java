package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidateApplicationExistsCommand;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.command.workitem.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Validates and dispatches WorkItem assignment commands. */
@Service
public class AssignCaseworkerService {

  private final CaseworkerRepository caseworkerRepository;
  private final CommandGateway commandGateway;

  /** Creates the assignment coordinator with its directory and command gateway. */
  public AssignCaseworkerService(
      CaseworkerRepository caseworkerRepository, CommandGateway commandGateway) {
    this.caseworkerRepository = caseworkerRepository;
    this.commandGateway = commandGateway;
  }

  /** Validates and assigns the caseworker to the given work item. */
  @Transactional
  public void assign(
      UUID caseworkerId, UUID applicationId, String serialisedRequest, String eventDescription) {
    if (!caseworkerRepository.existsById(caseworkerId)) {
      throw new ResourceNotFoundException("No caseworker found with id: " + caseworkerId);
    }
    commandGateway.sendAndWait(new ValidateApplicationExistsCommand(applicationId));
    commandGateway.sendAndWait(
        new AssignWorkItemCommand(
            WorkItemId.toAggregateId(applicationId),
            caseworkerId,
            serialisedRequest,
            eventDescription,
            Instant.now(),
            false));
  }
}
