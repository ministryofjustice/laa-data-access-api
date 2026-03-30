package uk.gov.justice.laa.dstew.access.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Collects query execution metrics and sends them to Micrometer.
 *
 * <p>Implements QueryExecutionListener from datasource-proxy to intercept
 * all SQL query executions and capture timing and statistics.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Lazy
public class QueryMetricsCollector implements QueryExecutionListener {

  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, Counter> queryCounterCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> queryTimerCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> slowQueryCounterCache = new ConcurrentHashMap<>();

  @Value("${datasource-proxy.slow-query-threshold-ms:100}")
  private long slowQueryThreshold;

  @Override
  public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
    // No action needed before query execution
  }

  @Override
  public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
    if (queryInfoList == null || queryInfoList.isEmpty()) {
      return;
    }

    long executionDuration = executionInfo.getElapsedTime();

    // Process each query in the execution
    for (QueryInfo queryInfo : queryInfoList) {
      String query = queryInfo.getQuery();
      String operationType = determineOperationType(query);

      queryCounterCache.computeIfAbsent(operationType, key ->
          Counter.builder("sql.queries.total")
              .description("Total number of SQL queries executed")
              .tag("operation_type", key)
              .register(meterRegistry)
      ).increment();

      queryTimerCache.computeIfAbsent(operationType, key ->
          Timer.builder("sql.execution.time")
              .description("SQL query execution time")
              .tag("operation_type", key)
              .publishPercentileHistogram(true)
              .register(meterRegistry)
      ).record(java.time.Duration.ofMillis(executionDuration));

      if (executionDuration > slowQueryThreshold) {
        slowQueryCounterCache.computeIfAbsent(operationType, key ->
            Counter.builder("sql.queries.slow")
                .description("Number of slow SQL queries exceeding threshold")
                .tag("operation_type", key)
                .register(meterRegistry)
        ).increment();

        log.warn("Slow query detected [{}ms > {}ms]: {}",
            executionDuration, slowQueryThreshold,
            query.length() > 100 ? query.substring(0, 100) + "..." : query);
      }
    }
  }

  /**
   * Determines the SQL operation type from the query string.
   *
   * @param query the SQL query string
   * @return operation type: "select", "insert", "update", "delete", or "other"
   */
  private String determineOperationType(String query) {
    if (query == null || query.isBlank()) {
      return "other";
    }

    String trimmedQuery = query.trim().toUpperCase();

    if (trimmedQuery.startsWith("SELECT")) {
      return "select";
    } else if (trimmedQuery.startsWith("INSERT")) {
      return "insert";
    } else if (trimmedQuery.startsWith("UPDATE")) {
      return "update";
    } else if (trimmedQuery.startsWith("DELETE")) {
      return "delete";
    }
    return "other";
  }
}
