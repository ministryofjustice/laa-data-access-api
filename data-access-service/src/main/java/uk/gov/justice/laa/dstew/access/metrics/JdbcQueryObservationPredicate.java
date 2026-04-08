package uk.gov.justice.laa.dstew.access.metrics;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import java.util.List;
import net.ttddyy.observation.tracing.QueryContext;
import org.springframework.stereotype.Component;

/**
 * Suppresses JDBC query observations for non-CRUD statements.
 *
 * <p>Only allows observations for standard data operations (SELECT, INSERT, UPDATE, DELETE, MERGE).
 * All other queries — transaction control, DDL, driver metadata, Flyway housekeeping — are
 * suppressed by returning {@code false}, which causes the observation to become
 * {@link Observation#NOOP} so no timer or histogram is recorded.</p>
 */
@Component
public class JdbcQueryObservationPredicate implements ObservationPredicate {

  @Override
  public boolean test(String name, Observation.Context context) {
    if ("jdbc.query".equals(name) && context instanceof QueryContext queryContext) {
      List<String> queries = queryContext.getQueries();
      if (queries != null && !queries.isEmpty()) {
        String trimmed = queries.getFirst().trim().toUpperCase();
        return isMeasuredQuery(trimmed);
      }
      return false;
    }
    return true;
  }

  private boolean isMeasuredQuery(String query) {
    return query.startsWith("SELECT")
        || query.startsWith("INSERT")
        || query.startsWith("UPDATE")
        || query.startsWith("DELETE")
        || query.startsWith("MERGE");
  }
}
