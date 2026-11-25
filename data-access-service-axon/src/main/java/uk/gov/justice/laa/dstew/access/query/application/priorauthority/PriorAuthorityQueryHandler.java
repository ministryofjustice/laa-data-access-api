package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import jakarta.annotation.Nullable;
import java.util.Optional;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;

/**
 * Answers single Prior Authority reads from its aggregate, folded from its events and their
 * content, so a read straight after a committed command sees that command on any pod.
 */
@Component
public class PriorAuthorityQueryHandler {

  /**
   * Returns the Prior Authority with its content, or {@code null} if it does not exist, its office
   * is outside the caller's read scope, or its content has been erased.
   */
  @Nullable
  @QueryHandler
  public PriorAuthorityResult handle(
      FindPriorAuthorityByPriorAuthorityIdQuery query,
      @InjectEntity(idProperty = "priorAuthorityId") @Nullable PriorAuthorityAggregate aggregate) {
    return state(aggregate)
        .filter(state -> query.accessScope().permitsOfficeCode(state.getOfficeCode()))
        .flatMap(
            state ->
                state.currentPayload().map(payload -> PriorAuthorityResult.from(state, payload)))
        .orElse(null);
  }

  /** Returns an owned, active document with its filename when available, or {@code null}. */
  @Nullable
  @QueryHandler
  public EvidenceDocument handle(
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
                                state
                                    .currentPayload()
                                    .map(PriorAuthorityDataPayload::documentFilenames)
                                    .map(filenames -> filenames.get(document.documentId()))
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
}
