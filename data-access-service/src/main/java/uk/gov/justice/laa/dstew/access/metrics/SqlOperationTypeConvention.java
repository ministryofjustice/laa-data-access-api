package uk.gov.justice.laa.dstew.access.metrics;

import io.micrometer.common.KeyValues;
import net.ttddyy.observation.tracing.QueryContext;
import net.ttddyy.observation.tracing.QueryObservationConvention;
import org.springframework.stereotype.Component;

/**
 * Custom observation convention that adds an {@code operation_type} tag to SQL query metrics.
 *
 * <p>Parses the SQL query string to determine the operation type (select, insert, update, delete)
 * and adds it as a low-cardinality key value on the {@code jdbc.query} observation. This preserves
 * the operation-type breakdowns used by the Grafana dashboard panels.
 */
@Component
public class SqlOperationTypeConvention implements QueryObservationConvention {

  @Override
  public KeyValues getLowCardinalityKeyValues(QueryContext context) {
    String operationType = "other";

    if (context.getQueries() != null && !context.getQueries().isEmpty()) {
      String query = context.getQueries().getFirst();
      operationType = determineOperationType(query);
    }

    return QueryObservationConvention.super
        .getLowCardinalityKeyValues(context)
        .and("operation_type", operationType);
  }

  private String determineOperationType(String query) {
    if (query == null || query.isBlank()) {
      return "other";
    }

    String trimmed = query.trim().toUpperCase();

    if (trimmed.startsWith("SELECT")) {
      return "select";
    } else if (trimmed.startsWith("INSERT")) {
      return "insert";
    } else if (trimmed.startsWith("UPDATE")) {
      return "update";
    } else if (trimmed.startsWith("DELETE")) {
      return "delete";
    } else if (trimmed.startsWith("MERGE")) {
      return "merge";
    }
    return "other";
  }
}
