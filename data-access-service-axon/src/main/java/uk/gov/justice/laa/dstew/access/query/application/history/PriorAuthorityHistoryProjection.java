package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataId;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

/** Projection that records Prior Authority activity to parent Application history. */
@Component
@RequiredArgsConstructor
@Namespace("prior-authority-history-projection")
public class PriorAuthorityHistoryProjection {

  private final PriorAuthorityDataRepository priorAuthorityDataRepository;
  private final PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository;

  /** Records a submitted Prior Authority in parent application's history. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        event.priorAuthorityId(),
        event.priorAuthorityType(),
        "PRIOR_AUTHORITY_SUBMITTED",
        event.dataVersion(),
        event.occurredAt());
  }

  /** Records a Prior Authority decision in the parent application's history. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        event.priorAuthorityId(),
        event.priorAuthorityType(),
        mapDecisionEventType(event),
        event.dataVersion(),
        event.occurredAt());
  }

  /** Records Prior Authority assignment in parent application's history. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    PriorAuthorityRef ref = lookupPriorAuthorityRef(event.workItemId());
    if (ref == null) {
      return;
    }

    append(
        message,
        ref.applicationId(),
        event.workItemId(),
        ref.priorAuthorityType(),
        "ASSIGN_APPLICATION_TO_CASEWORKER",
        event.itemVersion(),
        event.occurredAt());
  }

  /** Records Prior Authority unassignment in parent application's history. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    PriorAuthorityRef ref = lookupPriorAuthorityRef(event.workItemId());
    if (ref == null) {
      return;
    }

    append(
        message,
        ref.applicationId(),
        event.workItemId(),
        ref.priorAuthorityType(),
        "UNASSIGN_APPLICATION_TO_CASEWORKER",
        event.itemVersion(),
        event.occurredAt());
  }

  private static String mapDecisionEventType(PriorAuthorityDecisionMadeEvent event) {
    if (DecisionValue.GRANTED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED";
    }
    if (DecisionValue.REFUSED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_REFUSED";
    }
    throw new ApplicationHistoryIntegrityException(
        event.applicationId(),
        event.priorAuthorityId(),
        "unsupported prior-authority decision value: " + event.overallDecision());
  }

  private PriorAuthorityRef lookupPriorAuthorityRef(UUID priorAuthorityId) {
    return priorAuthorityDataRepository
        .findById(new PriorAuthorityDataId(priorAuthorityId, 0L))
        .map(
            pa -> {
              PriorAuthorityType type = pa.getPayload().content().priorAuthorityType();
              return new PriorAuthorityRef(
                  pa.getApplicationId(), type == null ? null : type.name());
            })
        .orElse(null);
  }

  private record PriorAuthorityRef(UUID applicationId, String priorAuthorityType) {}

  private void append(
      EventMessage message,
      UUID applicationId,
      UUID priorAuthorityId,
      String priorAuthorityType,
      String eventType,
      Long itemVersion,
      Instant occurredAt) {
    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(applicationId)
            .priorAuthorityId(priorAuthorityId)
            .priorAuthorityType(priorAuthorityType)
            .eventType(eventType)
            .itemVersion(itemVersion)
            .serviceName(getServiceName(message))
            .caseworkerId(getAuthenticatedUserId(message))
            .occurredAt(occurredAt)
            .build());
  }

  private static @Nullable String getServiceName(EventMessage message) {
    Object serviceName =
        message.metadata().get(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    return serviceName == null ? null : serviceName.toString();
  }

  private static @Nullable UUID getAuthenticatedUserId(EventMessage message) {
    Object authenticatedUserId =
        message.metadata().get(RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY);
    return authenticatedUserId == null ? null : UUID.fromString(authenticatedUserId.toString());
  }

  @ResetHandler
  public void reset() {
    priorAuthorityHistoryReadRepository.deleteAllInBatch();
  }
}
