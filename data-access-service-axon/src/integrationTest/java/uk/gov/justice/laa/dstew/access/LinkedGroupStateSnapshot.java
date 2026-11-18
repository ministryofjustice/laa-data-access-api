package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

/**
 * Persisted linked-group state used to prove that a no-op request changes nothing: group events,
 * justification payloads and each application's history rows.
 */
record LinkedGroupStateSnapshot(
    long groupEvents,
    long justificationPayloads,
    Map<UUID, List<Map<String, Object>>> historyRows,
    Map<UUID, Map<String, Long>> historyCountsByType) {

  static LinkedGroupStateSnapshot capture(
      JdbcTemplate jdbcTemplate, UUID groupId, Collection<UUID> applicationIds) {
    Map<UUID, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    Map<UUID, Map<String, Long>> counts = new LinkedHashMap<>();
    for (var applicationId : applicationIds) {
      var history =
          jdbcTemplate.queryForList(
              "SELECT * FROM axon.application_history WHERE application_id = ? ORDER BY event_id",
              applicationId);
      Map<String, Long> perType = new LinkedHashMap<>();
      for (var type : DomainEventType.values()) {
        perType.put(type.getValue(), 0L);
      }
      history.forEach(row -> perType.merge((String) row.get("event_type"), 1L, Long::sum));
      rows.put(applicationId, history);
      counts.put(applicationId, perType);
    }
    return new LinkedGroupStateSnapshot(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            Long.class,
            groupId.toString()),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.linked_group_justification WHERE group_id = ?",
            Long.class,
            groupId),
        rows,
        counts);
  }

  /** Waits for asynchronous history projection to settle, then asserts that nothing changed. */
  void assertUnchangedAfterProjectionsSettle(
      JdbcTemplate jdbcTemplate, UUID groupId, Collection<UUID> applicationIds) {
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(capture(jdbcTemplate, groupId, applicationIds)).isEqualTo(this));
  }
}
