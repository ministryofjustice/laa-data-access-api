package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/**
 * Retrieves and hydrates a Prior Authority submission, whether it is still an in-progress draft or
 * has already been submitted.
 */
@Service
public class GetPriorAuthorityUseCase {

  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final PriorAuthorityDraftStore priorAuthorityDraftStore;
  private final QueryGateway queryGateway;

  public GetPriorAuthorityUseCase(QueryGateway queryGateway) {
  /**
   * Constructor for GetPriorAuthorityUseCase.
   *
   * @param priorAuthorityDataStore The data store for submitted Prior Authorities.
   * @param priorAuthorityDraftStore The data store for in-progress draft Prior Authorities.
   * @param queryGateway The Axon QueryGateway used to query Prior Authority read models.
   */
  public GetPriorAuthorityUseCase(
      PriorAuthorityDataStore priorAuthorityDataStore,
      PriorAuthorityDraftStore priorAuthorityDraftStore,
      QueryGateway queryGateway) {
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.priorAuthorityDraftStore = priorAuthorityDraftStore;
    this.queryGateway = queryGateway;
  }

  /**
   * Retrieves the Prior Authority identified by its submission ID, falling back to its in-progress
   * draft content when it has not yet been submitted.
   */
  public PriorAuthorityResult getPriorAuthority(UUID priorAuthorityId) {
    PriorAuthorityReadModel priorAuthority =
        queryGateway
            .query(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                PriorAuthorityReadModel.class)
            .join();
    if (priorAuthority == null) {
      return priorAuthorityDraftStore
          .find(priorAuthorityId)
          .map(PriorAuthorityResult::fromDraft)
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "No prior authority found with ID: " + priorAuthorityId));
    }
    return priorAuthorityResult;
  }
}
