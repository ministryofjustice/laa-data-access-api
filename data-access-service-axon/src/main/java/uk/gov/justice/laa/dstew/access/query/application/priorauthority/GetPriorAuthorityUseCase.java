package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Retrieves a Prior Authority submission from its projection. */
@Service
public class GetPriorAuthorityUseCase {

  private final QueryGateway queryGateway;

  /**
   * Constructor for GetPriorAuthorityUseCase.
   *
   * @param queryGateway The Axon QueryGateway used to query Prior Authority read models.
   */
  public GetPriorAuthorityUseCase(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Retrieves the Prior Authority identified by its submission ID. */
  public PriorAuthorityResult getPriorAuthority(UUID priorAuthorityId) {
    PriorAuthorityResult priorAuthority =
        queryGateway
            .query(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                PriorAuthorityResult.class)
            .join();
    if (priorAuthority != null) {
      return priorAuthority;
    }
    throw new ResourceNotFoundException("No prior authority found with ID: " + priorAuthorityId);
  }
}
