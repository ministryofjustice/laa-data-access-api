package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;

/**
 * Answers single prior-authority reads from its aggregate, so a read straight after a committed
 * command sees that command on any pod.
 */
@Component
public class PriorAuthorityQueryHandler {

  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final PriorAuthorityDraftStore priorAuthorityDraftStore;

  public PriorAuthorityQueryHandler(
      PriorAuthorityDataStore priorAuthorityDataStore,
      PriorAuthorityDraftStore priorAuthorityDraftStore) {
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.priorAuthorityDraftStore = priorAuthorityDraftStore;
  }

  /** Returns the prior authority with its content, or {@code null} if absent or not visible. */
  @QueryHandler
  public @Nullable PriorAuthorityResult handle(
      FindPriorAuthorityByPriorAuthorityIdQuery query,
      @InjectEntity(idProperty = "priorAuthorityId") @Nullable PriorAuthorityAggregate aggregate) {
    return state(aggregate)
        .filter(state -> query.accessScope().permitsOfficeCode(state.getOfficeCode()))
        .flatMap(state -> content(state).map(payload -> PriorAuthorityResult.from(state, payload)))
        .orElse(null);
  }

  /** Returns a live document with its filename when available, or {@code null} if absent. */
  @QueryHandler
  public @Nullable EvidenceDocument handle(
      FindPriorAuthorityDocumentQuery query,
      @InjectEntity(idProperty = "priorAuthorityId") @Nullable PriorAuthorityAggregate aggregate) {
    return state(aggregate)
        .flatMap(
            state ->
                state.getUploadedDocuments().stream()
                    .filter(document -> query.documentId().equals(document.documentId()))
                    .filter(document -> !document.deleted())
                    .findFirst()
                    .map(
                        document ->
                            new EvidenceDocument(
                                document.documentId(),
                                document.documentType(),
                                content(state)
                                    .map(
                                        payload ->
                                            payload.documentFilenames().get(document.documentId()))
                                    .orElse(null),
                                null,
                                document.contentType(),
                                document.size(),
                                document.uploadedAt(),
                                document.sourceService(),
                                document.checksum(),
                                document.fileSuffix())))
        .orElse(null);
  }

  private static Optional<PriorAuthorityState> state(@Nullable PriorAuthorityAggregate aggregate) {
    return Optional.ofNullable(aggregate)
        .map(PriorAuthorityAggregate::getState)
        .filter(state -> state.getPriorAuthorityId() != null);
  }

  private Optional<PriorAuthorityDataPayload> content(PriorAuthorityState state) {
    UUID priorAuthorityId = state.getPriorAuthorityId();
    if (state.status() == PriorAuthorityStatus.DRAFT) {
      return priorAuthorityDraftStore.find(priorAuthorityId);
    }
    return Optional.ofNullable(
        priorAuthorityDataStore.get(priorAuthorityId, state.getDataVersion()));
  }
}
