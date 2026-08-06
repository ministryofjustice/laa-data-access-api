package uk.gov.justice.laa.dstew.access.command.workitem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Fans out linked-application WorkItem assignments to every member of the group. */
@Component
@Namespace("work-item-group-fan-out")
public class WorkItemGroupFanOutHandler {

  private final CommandGateway commandGateway;
  private final LinkedApplicationGroupReadRepository groupReadRepository;

  public WorkItemGroupFanOutHandler(
      CommandGateway commandGateway, LinkedApplicationGroupReadRepository groupReadRepository) {
    this.commandGateway = commandGateway;
    this.groupReadRepository = groupReadRepository;
  }

  @EventHandler
  public void on(WorkItemAssigned event) {
    try {
      if (!event.fanOut()) {
        resolveGroupSiblings(WorkItemId.toItemId(event.workItemId()))
            .forEach(
                siblingId ->
                    commandGateway.sendAndWait(
                        new AssignWorkItemCommand(
                            WorkItemId.toAggregateId(siblingId),
                            event.caseworkerId(),
                            event.serialisedRequest(),
                            event.eventDescription(),
                            event.occurredAt(),
                            true)));
      }
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Failed to process WorkItemAssigned for item "
              + WorkItemId.toItemId(event.workItemId())
              + ": "
              + exception.getClass().getSimpleName()
              + " - "
              + exception.getMessage(),
          exception);
    }
  }


  private List<UUID> resolveGroupSiblings(UUID workItemId) {
    Optional<LinkedApplicationGroupReadModel> asLead =
        groupReadRepository.findByLeadApplicationId(workItemId);
    if (asLead.isPresent()) {
      return siblingIds(asLead.get(), workItemId);
    }

    Optional<LinkedApplicationGroupReadModel> asMember =
        groupReadRepository.findByMemberIdsContaining(workItemId);
    return asMember.map(group -> siblingIds(group, workItemId)).orElseGet(List::of);
  }

  private List<UUID> siblingIds(LinkedApplicationGroupReadModel group, UUID workItemId) {
    List<UUID> ids = new ArrayList<>();
    ids.add(group.getLeadApplicationId());
    ids.addAll(group.getMemberIds());
    return ids.stream().filter(id -> !id.equals(workItemId)).distinct().toList();
  }
}
