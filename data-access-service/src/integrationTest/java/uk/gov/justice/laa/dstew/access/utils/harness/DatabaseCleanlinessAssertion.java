package uk.gov.justice.laa.dstew.access.utils.harness;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Queries every application-domain table and asserts each one is empty.
 * Intended to be called from @AfterEach in BaseHarnessTest (after tearDownTrackedData),
 * and from a dedicated end-of-suite marker test.
 *
 * <p>Table order is child → parent to respect FK constraints should the assertion
 * ever need to be extended to also delete rows.
 *
 * <p>The canonical list of tables and the Flyway seed count are defined in
 * {@link ApplicationDomainTables}.
 */
@Component
public class DatabaseCleanlinessAssertion {

    private final JdbcTemplate jdbc;

    public DatabaseCleanlinessAssertion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    /**
     * Asserts every table is empty, ignoring rows that were seeded by Flyway migrations.
     *
     * <p>The repeatable migration {@code R__insert_test_data.sql} inserts
     * {@value ApplicationDomainTables#FLYWAY_SEEDED_CASEWORKER_COUNT} caseworkers with fixed IDs on every
     * Testcontainers startup.  Those rows are part of the baseline dataset, not test
     * pollution, so they are subtracted from the caseworkers count before asserting.
     *
     * @param context human-readable label appended to failure messages (e.g. the test class name)
     */
    public void assertAllTablesEmpty(String context) {
        List<String> violations = new ArrayList<>();
        for (String table : ApplicationDomainTables.TABLES) {
            Integer rawCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            int count = rawCount == null ? 0 : rawCount;

            if ("caseworkers".equals(table)) {
                count = Math.max(0, count - ApplicationDomainTables.FLYWAY_SEEDED_CASEWORKER_COUNT);
            }

            if (count > 0) {
                violations.add(table + " has " + count + " unexpected row(s)");
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "[" + context + "] Database is not clean after test teardown:\n  "
                            + String.join("\n  ", violations)
            );
        }
    }
}

