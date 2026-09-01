package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteRepository;

class LinkedGroupWorkItemLifecycleHandlerTest {
  @Test
  void activatesOnlyAnApplicationWhoseDurableRouteResolvesToTheGroup() {
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    LinkedGroupWorkItemLifecycleHandler handler =
        new LinkedGroupWorkItemLifecycleHandler(routes, gateway);
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    when(routes.findByWorkItemId(applicationId))
        .thenReturn(
            Optional.of(
                new WorkItemRoute(
                    WorkItemType.APPLICATION,
                    applicationId,
                    WorkItemRouteKind.LINKED_GROUP,
                    groupId,
                    groupId,
                    2L,
                    occurredAt)));

    handler.on(new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 4L, occurredAt));

    verify(gateway)
        .sendAndWait(
            new ActivateLinkedGroupMemberWorkItemCommand(groupId, applicationId, 2L, occurredAt));
  }
}
