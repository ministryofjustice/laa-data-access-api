package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads committed Application aggregate positions from the Axon JPA event store. */
@Repository
public class ApplicationEventStorePositionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final String eventStoreTable;

  public ApplicationEventStorePositionRepository(
      JdbcTemplate jdbcTemplate,
      @Value("${spring.jpa.properties.hibernate.default_schema:${axon.db.schema:axon}}")
          String schema) {
    this.jdbcTemplate = jdbcTemplate;
    this.eventStoreTable = schema + ".domain_event_entry";
  }

  /** Returns the latest committed event sequence for an aggregate. */
  public long latestSequence(UUID applicationId) {
    Long sequence =
        jdbcTemplate.queryForObject(
            "SELECT MAX(sequence_number) FROM "
                + eventStoreTable
                + " WHERE aggregate_identifier = ?",
            Long.class,
            applicationId.toString());
    if (sequence == null) {
      throw new IllegalStateException(
          "No committed event sequence found for Application ID: " + applicationId);
    }
    return sequence;
  }

  /** Returns the aggregate sequence for one committed event. */
  public long sequenceForEvent(UUID applicationId, String eventIdentifier) {
    Long sequence =
        jdbcTemplate.queryForObject(
            "SELECT sequence_number FROM "
                + eventStoreTable
                + " "
                + "WHERE aggregate_identifier = ? AND event_identifier = ?",
            Long.class,
            applicationId.toString(),
            eventIdentifier);
    if (sequence == null) {
      throw new IllegalStateException(
          "No committed event sequence found for event ID: " + eventIdentifier);
    }
    return sequence;
  }
}
