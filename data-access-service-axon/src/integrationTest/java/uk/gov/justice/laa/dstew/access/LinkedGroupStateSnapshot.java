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

/**
 * Persisted linked-group state used to prove that a no-op request changes nothing: group events,
 * justification payloads and each application's lifecycle stream, from which its history is read.
 */
record LinkedGroupStateSnapshot(
    long groupEvents,
    long justificationPayloads,
    Map<UUID, List<Map<String, Object>>> lifecycleStreams,
    Map<UUID, Map<String, Long>> lifecycleCountsByEvent) {

  static LinkedGroupStateSnapshot capture(
      JdbcTemplate jdbcTemplate, UUID groupId, Collection<UUID> applicationIds) {
    Map<UUID, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    Map<UUID, Map<String, Long>> counts = new LinkedHashMap<>();
    for (var applicationId : applicationIds) {
      var stream =
          jdbcTemplate.queryForList(
              "SELECT source, event_identifier, event_name, occurred_at FROM axon.lifecycle_stream"
                  + " WHERE genesis_id = ? ORDER BY occurred_at, id",
              applicationId);
      Map<String, Long> perEvent = new LinkedHashMap<>();
      stream.forEach(row -> perEvent.merge((String) row.get("event_name"), 1L, Long::sum));
      rows.put(applicationId, stream);
      counts.put(applicationId, perEvent);
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

  /** Waits for the asynchronous lifecycle stream to settle, then asserts that nothing changed. */
  void assertUnchangedAfterProjectionsSettle(
      JdbcTemplate jdbcTemplate, UUID groupId, Collection<UUID> applicationIds) {
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(capture(jdbcTemplate, groupId, applicationIds)).isEqualTo(this));
  }
}
