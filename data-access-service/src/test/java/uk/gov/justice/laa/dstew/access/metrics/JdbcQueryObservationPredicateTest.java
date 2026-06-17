package uk.gov.justice.laa.dstew.access.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.observation.Observation;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.ttddyy.observation.tracing.QueryContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JdbcQueryObservationPredicateTest {

  private final JdbcQueryObservationPredicate predicate = new JdbcQueryObservationPredicate();

  @ParameterizedTest
  @MethodSource("suppressedQueries")
  void test_suppressesNonCrudQueries(String query) {
    QueryContext context = new QueryContext();
    context.setQueries(List.of(query));
    assertFalse(predicate.test("jdbc.query", context));
  }

  private static Stream<Arguments> suppressedQueries() {
    return Stream.of(
        Arguments.of("BEGIN"),
        Arguments.of("COMMIT"),
        Arguments.of("ROLLBACK"),
        Arguments.of("SET ROLE my_role"),
        Arguments.of("SET search_path TO public"),
        Arguments.of("  BEGIN"),
        Arguments.of("SHOW TRANSACTION ISOLATION LEVEL"),
        Arguments.of("CREATE TABLE IF NOT EXISTS flyway_schema_history"),
        Arguments.of("ALTER TABLE foo ADD col int"),
        Arguments.of("LOCK TABLE flyway_schema_history"));
  }

  @ParameterizedTest
  @MethodSource("allowedQueries")
  void test_allowsRegularQueries(String query) {
    QueryContext context = new QueryContext();
    context.setQueries(List.of(query));
    assertTrue(predicate.test("jdbc.query", context));
  }

  private static Stream<Arguments> allowedQueries() {
    return Stream.of(
        Arguments.of("SELECT * FROM table"),
        Arguments.of("INSERT INTO table VALUES (1)"),
        Arguments.of("UPDATE table SET col = 1"),
        Arguments.of("DELETE FROM table WHERE id = 1"),
        Arguments.of("MERGE INTO table USING source ON condition"),
        Arguments.of("  SELECT * FROM table"));
  }

  @ParameterizedTest
  @MethodSource("nonJdbcContexts")
  void test_allowsNonJdbcQueryObservations(String observationName, Observation.Context context) {
    assertTrue(predicate.test(observationName, context));
  }

  private static Stream<Arguments> nonJdbcContexts() {
    return Stream.of(
        Arguments.of("http.server.requests", new Observation.Context()),
        Arguments.of("other.observation", new QueryContext()));
  }

  @ParameterizedTest
  @MethodSource("edgeCaseContexts")
  void test_suppressesEdgeCaseContexts(QueryContext context) {
    assertFalse(predicate.test("jdbc.query", context));
  }

  private static Stream<Arguments> edgeCaseContexts() {
    QueryContext nullQueries = new QueryContext();
    nullQueries.setQueries(null);

    QueryContext emptyQueries = new QueryContext();
    emptyQueries.setQueries(Collections.emptyList());

    return Stream.of(Arguments.of(nullQueries), Arguments.of(emptyQueries));
  }
}
