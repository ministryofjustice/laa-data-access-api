package uk.gov.justice.laa.dstew.access.utils.harness;

import java.util.List;

/**
 * Single source of truth for the list of application-domain tables.
 *
 * <p>Tables are ordered child → parent to respect FK constraints should any
 * cleanup extension ever need to delete rows in this order.
 *
 * <p>Names verified against Flyway migration scripts:
 * V1__initial_setup.sql, V4__add_decisions_table.sql, V5__add_merits_tables.sql,
 * V8__add_proceedings_table.sql, V9__add_linked_applications_table.sql,
 * V14__change_decision_relationship.sql, V16__add_certificates_table.sql.
 *
 * <p>Note: after V14 the FK runs {@code applications.decision_id → decisions}, so
 * decisions are NOT cascade-deleted when their parent application is deleted.
 */
public final class ApplicationDomainTables {

    private ApplicationDomainTables() {}

    public static final List<String> TABLES = List.of(
            "domain_events",
            "linked_merits_decisions",   // join table: decisions ↔ merits_decisions
            "merits_decisions",
            "decisions",
            "proceedings",
            "certificates",
            "linked_applications",
            "linked_individuals",        // join table: applications ↔ individuals
            "individuals",
            "applications",
            "caseworkers"
    );

    /**
     * IDs of caseworkers inserted by R__insert_test_data.sql (Flyway repeatable migration).
     * These rows are part of the baseline dataset and must not be counted as test pollution.
     */
    public static final int FLYWAY_SEEDED_CASEWORKER_COUNT = 11;
}

