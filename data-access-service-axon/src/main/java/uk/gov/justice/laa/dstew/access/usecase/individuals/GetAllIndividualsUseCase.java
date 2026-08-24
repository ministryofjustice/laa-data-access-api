package uk.gov.justice.laa.dstew.access.usecase.individuals;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured use case for retrieving paginated individuals. */
@Service
public class GetAllIndividualsUseCase {

  private final QueryGateway queryGateway;

  public GetAllIndividualsUseCase(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Returns a filtered, paginated list of individuals. */
  @AllowApiCaseworker
  public FindIndividualsResult execute(FindIndividualsQuery query) {
    return queryGateway.query(query, FindIndividualsResult.class).join();
  }
}
