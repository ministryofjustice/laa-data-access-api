package util;

import static org.awaitility.Awaitility.await;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;

/** Polls query projections until they reach an expected state. */
public class ProjectionAwaiter {

  private final QueryGateway queryGateway;

  public ProjectionAwaiter(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  public ApplicationReadModel awaitApplication(UUID applicationId) {
    return awaitUntil(
        "application projection to be populated for " + applicationId,
        () -> findApplication(applicationId),
        Objects::nonNull);
  }

  public ApplicationReadModel awaitApplicationVersion(UUID applicationId, long version) {
    return awaitUntil(
        "application projection to reach version " + version + " for " + applicationId,
        () -> findApplication(applicationId),
        projected -> projected != null && projected.getApplicationDataVersion() == version);
  }

  public PriorAuthorityResult awaitPriorAuthority(UUID priorAuthorityId) {
    return awaitUntil(
        "prior authority projection to be populated for " + priorAuthorityId,
        () -> findPriorAuthority(priorAuthorityId),
        Objects::nonNull);
  }

  public PriorAuthorityResult awaitDecidedPriorAuthority(UUID priorAuthorityId) {
    return awaitUntil(
        "prior authority projection to be decided for " + priorAuthorityId,
        () -> findPriorAuthority(priorAuthorityId),
        projected ->
            projected != null
                && "DECIDED".equals(projected.status())
                && projected.decisionDetails() != null);
  }

  private ApplicationReadModel findApplication(UUID applicationId) {
    return queryGateway
        .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
        .join();
  }

  private PriorAuthorityResult findPriorAuthority(UUID priorAuthorityId) {
    return queryGateway
        .query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            PriorAuthorityResult.class)
        .join();
  }

  private static <T> T awaitUntil(String alias, Callable<T> query, Predicate<T> condition) {
    return await()
        .alias(alias)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(query, condition);
  }
}
