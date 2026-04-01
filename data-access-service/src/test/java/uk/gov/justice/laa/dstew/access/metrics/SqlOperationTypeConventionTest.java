package uk.gov.justice.laa.dstew.access.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.ttddyy.observation.tracing.QueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlOperationTypeConventionTest {

  private final SqlOperationTypeConvention convention = new SqlOperationTypeConvention();

  @ParameterizedTest
  @MethodSource("provideQueries")
  void testDetermineOperationType(String query, String expected) {
    QueryContext context = new QueryContext();
    context.setQueries(List.of(query));
    KeyValues lowCardinalityKeyValues = convention.getLowCardinalityKeyValues(context);
    String cardinalValue = lowCardinalityKeyValues.stream()
        .filter(kv -> kv.getKey().equals("operation_type"))
        .map(KeyValue::getValue)
        .findFirst().orElseThrow();
    assertEquals(expected, cardinalValue);
  }

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.of("SELECT * FROM table WHERE id = 1", "select"),
        Arguments.of("INSERT INTO table VALUES (1)", "insert"),
        Arguments.of("UPDATE table SET col = 1 WHERE id = 1", "update"),
        Arguments.of("DELETE FROM table WHERE id = 1", "delete"),
        Arguments.of("  SELECT * FROM table", "select"),
        Arguments.of("MERGE INTO table USING source ON condition", "merge"),
        Arguments.of("ALTER TABLE foo ADD col int", "other"),
        Arguments.of("", "other")
    );
  }

  @Test
  void getLowCardinalityKeyValues_nullQueries_returnsOther() {
    QueryContext context = new QueryContext();
    context.setQueries(null);
    KeyValues lowCardinalityKeyValues = convention.getLowCardinalityKeyValues(context);
    String cardinalValue = lowCardinalityKeyValues.stream()
        .filter(kv -> kv.getKey().equals("operation_type"))
        .map(KeyValue::getValue)
        .findFirst().orElseThrow();
    assertEquals("other", cardinalValue);
  }

  @Test
  void getLowCardinalityKeyValues_emptyQueries_returnsOther() {
    QueryContext context = new QueryContext();
    context.setQueries(Collections.emptyList());
    KeyValues lowCardinalityKeyValues = convention.getLowCardinalityKeyValues(context);
    String cardinalValue = lowCardinalityKeyValues.stream()
        .filter(kv -> kv.getKey().equals("operation_type"))
        .map(KeyValue::getValue)
        .findFirst().orElseThrow();
    assertEquals("other", cardinalValue);
  }
}