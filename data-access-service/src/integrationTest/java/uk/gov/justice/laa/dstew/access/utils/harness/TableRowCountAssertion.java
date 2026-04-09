package uk.gov.justice.laa.dstew.access.utils.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a snapshot of row counts for every application-domain table and,
 * later, asserts that those counts have not changed.
 *
 * <p>Unlike {@link DatabaseCleanlinessAssertion} (which asserts tables are empty),
 * this class compares a <em>before</em> snapshot to an <em>after</em> snapshot.
 * This makes it suitable for infrastructure / smoke tests that run against a
 * live environment where tables are legitimately non-empty before the suite starts.
 *
 * <p>No Flyway-seed offset is applied here because we are comparing before vs. after,
 * not comparing against zero — whatever rows exist at snapshot time are expected to
 * still exist when the suite completes.
 */
@Component
public class TableRowCountAssertion {

    private static final Logger log = LoggerFactory.getLogger(TableRowCountAssertion.class);

    private final JdbcTemplate jdbc;

    public TableRowCountAssertion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Captures the current row count for every application-domain table.
     *
     * @return an ordered map of {@code tableName → rowCount}
     */
    public Map<String, Long> captureRowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : ApplicationDomainTables.TABLES) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            counts.put(table, count == null ? 0L : count);
        }
        log.info("[TableRowCountAssertion] Row counts captured: {}", counts);
        return counts;
    }

    /**
     * Asserts that the current row counts for all application-domain tables match
     * the previously captured {@code before} snapshot.
     *
     * <p>A mismatch means that a test inserted or deleted rows without cleaning up.
     *
     * @param before  the snapshot captured before the suite ran (from {@link #captureRowCounts()})
     * @param context human-readable label appended to failure messages (e.g. "full-infrastructure-suite")
     * @throws AssertionError if any table's row count differs from the snapshot
     */
    public void assertRowCountsMatch(Map<String, Long> before, String context) {
        log.info("[TableRowCountAssertion] Running row-count parity assertion for context '{}'", context);
        Map<String, Long> after = captureRowCounts();
        List<String> violations = new ArrayList<>();

        for (String table : ApplicationDomainTables.TABLES) {
            long beforeCount = before.getOrDefault(table, 0L);
            long afterCount = after.getOrDefault(table, 0L);
            if (beforeCount != afterCount) {
                violations.add(String.format(
                        "%s: was %d row(s) before suite, found %d row(s) after (delta: %+d)",
                        table, beforeCount, afterCount, afterCount - beforeCount));
            }
        }

        if (!violations.isEmpty()) {
            log.error("[TableRowCountAssertion] FAILED — row count mismatches:\n  {}", String.join("\n  ", violations));
            throw new AssertionError(
                    "[" + context + "] Table row counts changed during the test suite — " +
                    "data may not have been cleaned up:\n  " +
                    String.join("\n  ", violations)
            );
        }

        log.info("[TableRowCountAssertion] PASSED — all table row counts match the before-suite snapshot");
    }
}

