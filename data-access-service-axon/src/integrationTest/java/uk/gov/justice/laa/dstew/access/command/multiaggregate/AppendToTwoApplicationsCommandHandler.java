package uk.gov.justice.laa.dstew.access.command.multiaggregate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Test-only handler that appends assignment events to two applications in one command. */
@Component
public class AppendToTwoApplicationsCommandHandler {

  public static final AtomicReference<Runnable> BEFORE_APPEND = new AtomicReference<>(() -> {});
  public static final AtomicBoolean SAME_INSTANCE_WHEN_RELOADED = new AtomicBoolean();

  /** Loads both applications and appends their assignment events atomically. */
  @CommandHandler
  public void handle(
      AppendToTwoApplicationsCommand command,
      @InjectEntity(idProperty = "firstApplicationId") ApplicationAggregate first,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var second =
        stateManager
            .loadEntity(ApplicationAggregate.class, command.secondApplicationId(), context)
            .join();
    var firstAgain =
        stateManager
            .loadEntity(ApplicationAggregate.class, command.firstApplicationId(), context)
            .join();
    SAME_INSTANCE_WHEN_RELOADED.set(firstAgain == first);
    BEFORE_APPEND.get().run();
    eventAppender.append(
        assigned(first.getState(), command.caseworkerId(), command.occurredAt()),
        assigned(second.getState(), command.caseworkerId(), command.occurredAt()));
  }

  private static WorkItemAssigned assigned(
      ApplicationState state, UUID caseworkerId, Instant occurredAt) {
    return new WorkItemAssigned(
        state.getApplicationId(),
        WorkItemType.APPLICATION,
        state.getApplicationVersion(),
        state.getAssignmentVersion() + 1,
        caseworkerId,
        occurredAt);
  }
}
