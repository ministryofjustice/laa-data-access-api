package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Retrieves and hydrates the current Prior Authority submission. */
@Service
public class GetPriorAuthorityUseCase {

  private final QueryGateway queryGateway;

  public GetPriorAuthorityUseCase(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Retrieves the Prior Authority identified by its submission ID. */
  public PriorAuthorityResult getPriorAuthority(UUID priorAuthorityId) {
    return requirePriorAuthority(
        queryGateway
            .query(
                new FindPriorAuthorityBySubmissionIdQuery(priorAuthorityId),
                PriorAuthorityResult.class)
            .join(),
        priorAuthorityId);
  }

  private PriorAuthorityResult requirePriorAuthority(
      @Nullable PriorAuthorityResult priorAuthorityResult, UUID priorAuthorityId) {
    if (priorAuthorityResult == null) {
      throw new ResourceNotFoundException("No prior authority found with ID: " + priorAuthorityId);
    }
    return priorAuthorityResult;
  }
}
