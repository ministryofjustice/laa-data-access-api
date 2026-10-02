package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.jspecify.annotations.NonNull;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/**
 * Event-sourced consistency boundary for a PriorAuthority submission.
 *
 * <p>On the first command for this aggregate ID, persists version 0 of the sensitive data and emits
 * {@link PriorAuthorityDraftStartedEvent}.
 */
@EventSourced(tagKey = "PriorAuthorityAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityAggregate {

  private UUID priorAuthorityId;
  private final PriorAuthorityState state = new PriorAuthorityState();

  private static @NonNull List<PriorAuthorityDocument> getUpdatedDocuments(
      PriorAuthorityDocumentTypeUpdateCommand command, PriorAuthorityDataPayload existingDraft) {
    List<PriorAuthorityDocument> updatedDocuments = copyUploadedDocuments(existingDraft);
    int documentIndex =
        IntStream.range(0, updatedDocuments.size())
            .filter(index -> updatedDocuments.get(index).documentId().equals(command.documentId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Document %s not found for Prior Authority %s"
                            .formatted(command.documentId(), command.priorAuthorityId())));
    PriorAuthorityDocument existingDocument = updatedDocuments.get(documentIndex);
    updatedDocuments.set(documentIndex, existingDocument.withDocumentType(command.documentType()));
    return updatedDocuments;
  }

  private static @NonNull PriorAuthorityDataPayload requireDraft(
      UUID priorAuthorityId, PriorAuthorityDraftStore draftStore) {
    return draftStore
        .find(priorAuthorityId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Prior Authority %s not found".formatted(priorAuthorityId)));
  }

  private static List<PriorAuthorityDocument> copyUploadedDocuments(
      PriorAuthorityDataPayload draft) {
    return draft.content().uploadedDocuments() == null
        ? new ArrayList<>()
        : new ArrayList<>(draft.content().uploadedDocuments());
  }

  public UUID getApplicationId() {
    return state.applicationId;
  }

  public String getPriorAuthorityType() {
    return state.priorAuthorityType;
  }

  public UUID getPriorAuthorityId() {
    return priorAuthorityId;
  }

  public long getDataVersion() {
    return state.dataVersion;
  }

  public long getAssignmentVersion() {
    return state.assignmentVersion;
  }

  public UUID getCaseworkerId() {
    return state.caseworkerId;
  }

  public PriorAuthorityState getState() {
    return state;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDraftStartedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthoritySubmittedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDecisionMadeEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDocumentUploadedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDocumentDeletedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDocumentTypeUpdatedEvent event) {}

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EntityCreator
  protected PriorAuthorityAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
