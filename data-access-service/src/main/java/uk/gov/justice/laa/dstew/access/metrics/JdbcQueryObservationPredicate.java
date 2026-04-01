package uk.gov.justice.laa.dstew.access.metrics;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import java.util.List;
import net.ttddyy.observation.tracing.QueryContext;
import org.springframework.stereotype.Component;

/**
 * Suppresses JDBC query observations for transaction control and SET statements.
 *
 * <p>Queries like {@code BEGIN}, {@code COMMIT}, {@code ROLLBACK}, and {@code SET} are
 * infrastructure noise that inflate the "other" bucket in Prometheus metrics. Returning
 * {@code false} causes the observation to become {@link Observation#NOOP}, so no timer
 * or histogram is recorded.</p>
 */
@Component
public class JdbcQueryObservationPredicate implements ObservationPredicate {

  @Override
  public boolean test(String name, Observation.Context context) {
    if ("jdbc.query".equals(name) && context instanceof QueryContext queryContext) {
      List<String> queries = queryContext.getQueries();
      if (queries != null && !queries.isEmpty()) {
        String trimmed = queries.getFirst().trim().toUpperCase();
        return !isIgnoredQuery(trimmed);
      }
    }
    return true;
  }

  private boolean isIgnoredQuery(String query) {
    return query.startsWith("BEGIN")
        || query.startsWith("COMMIT")
        || query.startsWith("ROLLBACK")
        || query.startsWith("SET");
  }
}
