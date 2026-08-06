package uk.gov.justice.laa.dstew.access.command.workitem;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Proves that the specified caseworker is currently assigned to a WorkItem. */
@Command(routingKey = "workItemId")
public record ValidateWorkItemAssignmentCommand(
    @TargetEntityId UUID workItemId, UUID applicationId, UUID caseworkerId) {}

