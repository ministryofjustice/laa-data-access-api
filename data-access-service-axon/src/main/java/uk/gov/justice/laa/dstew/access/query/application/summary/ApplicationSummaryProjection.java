package uk.gov.justice.laa.dstew.access.query.application.summary;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.eventcontent.RegisteredContentReaders;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Builds the application summary from events. It is a registered content reader: descriptive facts
 * come from each event's erasable content, so erased content leaves those fields empty while kept
 * fields (status, office, caseworker, group, timestamps) remain.
 */
@Component
@SequencingPolicy
@Namespace(RegisteredContentReaders.APPLICATION_SUMMARY)
public class ApplicationSummaryProjection {

  private final ApplicationSummaryReadRepository summaryRepository;

  /** Constructs the projection with its repository. */
  public ApplicationSummaryProjection(ApplicationSummaryReadRepository summaryRepository) {
    this.summaryRepository = summaryRepository;
  }

  /** Inserts the summary row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage message) {
    ApplicationSummaryReadModel row =
        ApplicationSummaryReadModel.builder()
            .applicationId(event.applicationId())
            .status(event.status())
            .laaReference(event.laaReference())
            .officeCode(event.officeCode())
            .caseworkerId(null)
            .autoGranted(AutoGrantedState.PENDING)
            .submittedAt(event.occurredAt())
            .modifiedAt(event.occurredAt())
            .linkedGroupId(null)
            .leadApplicationId(null)
            .streamVersion(0L)
            .projectionPosition(message.identifier().hashCode())
            .build();
    applyContent(row, event.content().orElse(null));
    summaryRepository.save(row);
  }

  /** Records group membership when a linked group is established explicitly. */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, EventMessage message) {
    event
        .memberApplicationIds()
        .forEach(
            memberApplicationId ->
                updateGroup(
                    memberApplicationId,
                    event.groupId(),
                    memberApplicationId.equals(event.leadApplicationId())
                        ? null
                        : event.leadApplicationId(),
                    event.occurredAt(),
                    message));
  }

  /** Records group membership when a member joins an existing linked group. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event, EventMessage message) {
    updateGroup(
        event.memberId(), event.groupId(), event.leadApplicationId(), event.occurredAt(), message);
  }

  /** Moves the lead reference for every application in the changed group. */
  @EventHandler
  public void on(LinkedApplicationGroupLeadChangedEvent event, EventMessage message) {
    summaryRepository
        .findAllByLinkedGroupId(event.groupId())
        .forEach(
            row -> {
              row.setLeadApplicationId(
                  row.getApplicationId().equals(event.newLeadApplicationId())
                      ? null
                      : event.newLeadApplicationId());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /** Clears group membership when a member leaves a group. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event, EventMessage message) {
    updateGroup(event.memberId(), null, null, event.occurredAt(), message);
  }

  /** Clears group membership from every member when a group is dissolved. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event, EventMessage message) {
    event
        .memberApplicationIds()
        .forEach(memberId -> updateGroup(memberId, null, null, event.occurredAt(), message));
  }

  /**
   * Updates {@code status}, {@code is_auto_granted}, and {@code stream_version} when a decision is
   * made.
   */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage message) {
    ApplicationStatus applicationStatus;
    if (event.overallDecision() == null || event.overallDecision().isBlank()) {
      applicationStatus = null;

    } else {
      applicationStatus =
          DecisionValue.valueOf(event.overallDecision()).equals(DecisionValue.REFUSED)
              ? ApplicationStatus.APPLICATION_REFUSED
              : ApplicationStatus.APPLICATION_GRANTED;
    }
    summaryRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setStatus(applicationStatus != null ? applicationStatus.name() : row.getStatus());
              row.setAutoGranted(event.autoGranted());
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /** Records that automatic assessment completed with a manual-assessment outcome. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event, EventMessage message) {
    summaryRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setAutoGranted(AutoGrantedState.MANUAL);
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /** Refreshes descriptive facts from the replacement content. */
  @EventHandler
  public void on(ApplicationUpdatedEvent event, EventMessage message) {
    summaryRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setStatus(event.status());
              if (event.enteredSubmitted()) {
                row.setAutoGranted(AutoGrantedState.PENDING);
              }
              applyContent(row, event.content().orElse(null));
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /**
   * Mirrors generic direct application assignment without making this search index authoritative.
   */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (event.workItemType() != WorkItemType.APPLICATION) {
      return;
    }
    summaryRepository
        .findById(event.workItemId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(event.caseworkerId());
              row.setStreamVersion(event.itemVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /**
   * Mirrors generic direct application unassignment without making this search index authoritative.
   */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (event.workItemType() != WorkItemType.APPLICATION) {
      return;
    }
    summaryRepository
        .findById(event.workItemId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(null);
              row.setStreamVersion(event.itemVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /**
   * Updates {@code projection_position} when a note is created.
   *
   * <p>Note creation does not change any filter or sort field on the index, so only the position
   * bookkeeping column is updated.
   */
  @EventHandler
  public void on(NoteCreatedEvent event, EventMessage message) {
    summaryRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /** Updates application list ordering after an upload without loading filenames. */
  @EventHandler
  public void on(ApplicationDocumentUploadedEvent event, EventMessage message) {
    summaryRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setModifiedAt(event.uploadedAt());
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  private void updateGroup(
      UUID applicationId,
      UUID linkedGroupId,
      UUID leadApplicationId,
      Instant occurredAt,
      EventMessage message) {
    summaryRepository
        .findById(applicationId)
        .ifPresent(
            row -> {
              row.setLinkedGroupId(linkedGroupId);
              row.setLeadApplicationId(leadApplicationId);
              row.setModifiedAt(occurredAt);
              row.setProjectionPosition(message.identifier().hashCode());
              summaryRepository.save(row);
            });
  }

  /**
   * Copies descriptive facts from content. Erased content clears them; the office code falls back
   * to the row's kept value so office access still applies.
   */
  private static void applyContent(
      ApplicationSummaryReadModel row, ParsedAppContentDetails content) {
    record MatterPair(String name, String code) {}

    ApplicationClient client = content == null ? null : content.client();
    row.setClientFirstName(client == null ? null : client.getFirstName());
    row.setClientLastName(client == null ? null : client.getLastName());
    row.setClientDateOfBirth(client == null ? null : client.getDateOfBirth());
    row.setCategoryOfLaw(content == null ? null : content.categoryOfLaw());
    row.setCategoryOfLawCode(content == null ? null : content.categoryOfLawCode());
    row.setMatterType(content == null ? null : content.matterType());
    row.setMatterTypeCode(content == null ? null : content.matterTypeCode());
    row.setUsedDelegatedFunctions(content == null ? null : content.usedDelegatedFunctions());
    List<MatterPair> matterPairs =
        (content == null || content.proceedings() == null
                ? Stream.<Proceeding>empty()
                : content.proceedings().stream())
            .filter(Objects::nonNull)
            .map(
                proceeding ->
                    new MatterPair(proceeding.getMatterType(), proceeding.getMatterTypeCode()))
            .distinct()
            .toList();
    row.setMatterTypes(matterPairs.stream().map(MatterPair::name).toList());
    row.setMatterTypeCodes(matterPairs.stream().map(MatterPair::code).toList());
    if (content != null
        && content.provider() != null
        && content.provider().getOfficeCode() != null) {
      row.setOfficeCode(content.provider().getOfficeCode());
    }
  }

  /** Clears the summary before replay. */
  @ResetHandler
  public void reset() {
    summaryRepository.deleteAllInBatch();
  }
}
