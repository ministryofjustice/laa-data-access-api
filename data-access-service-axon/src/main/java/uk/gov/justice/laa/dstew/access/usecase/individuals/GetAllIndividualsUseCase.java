package uk.gov.justice.laa.dstew.access.usecase.individuals;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadAccessPolicyProvider;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured use case for retrieving paginated individuals. */
@Service
public class GetAllIndividualsUseCase {

  private final QueryGateway queryGateway;
  private final ApplicationReadAccessPolicyProvider accessPolicyProvider;

  public GetAllIndividualsUseCase(
      QueryGateway queryGateway, ApplicationReadAccessPolicyProvider accessPolicyProvider) {
    this.queryGateway = queryGateway;
    this.accessPolicyProvider = accessPolicyProvider;
  }

  /** Returns a filtered, paginated list of individuals. */
  @AllowApiCaseworker
  public FindIndividualsResult execute(FindIndividualsQuery query) {
    FindIndividualsQuery scopedQuery =
        new FindIndividualsQuery(
            query.applicationId(),
            query.individualType(),
            query.includeClientDetails(),
            query.page(),
            query.pageSize(),
            accessPolicyProvider.resolve());
    return queryGateway.query(scopedQuery, FindIndividualsResult.class).join();
  }
}
