package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryProjection;

class LinkedGroupJustificationIntegrationTest extends AbstractLinkedGroupIntegrationTest {
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApplicationHistoryProjection historyProjection;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Test
  void
      givenGroupLifecycle_whenActionsAccepted_thenEveryMemberHasSharedJustificationAndDirectProjectionReplay() {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    var joining = createApplication(surname);
    assertThat(linkWithReason(member, lead, null, "Create reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_CREATED, "Create reason");
    assertHistory(member, DomainEventType.APPLICATION_GROUP_JOINED, "Create reason");
    assertSharedReference(group, "Create reason", 2);

    assertThat(linkWithReason(joining, lead, groupToken(group, 0), "Add reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertHistory(joining, DomainEventType.APPLICATION_GROUP_JOINED, "Add reason");
    for (var id : List.of(lead, member)) {
      assertHistory(id, DomainEventType.APPLICATION_GROUP_MEMBER_ADDED, "Add reason");
    }
    assertSharedReference(group, "Add reason", 3);

    assertThat(change(member, "make-lead", group, 1, "Lead reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    for (var id : List.of(lead, member, joining)) {
      assertHistory(id, DomainEventType.APPLICATION_GROUP_LEAD_CHANGED, "Lead reason");
    }
    assertSharedReference(group, "Lead reason", 3);

    assertThat(change(joining, "unlink", group, 2, "Remove reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertHistory(joining, DomainEventType.APPLICATION_GROUP_LEFT, "Remove reason");
    for (var id : List.of(lead, member)) {
      assertHistory(id, DomainEventType.APPLICATION_GROUP_MEMBER_REMOVED, "Remove reason");
    }
    assertSharedReference(group, "Remove reason", 3);

    assertThat(change(lead, "unlink", group, 3, "Dissolve reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_LEFT, "Dissolve reason");
    assertHistory(member, DomainEventType.APPLICATION_GROUP_DISSOLVED, "Dissolve reason");
    assertSharedReference(group, "Dissolve reason", 2);
    assertThat(payloadCount(group)).isEqualTo(5);

    var stored =
        jdbcTemplate.queryForList(
            "SELECT event_identifier, payload_type, payload, meta_data FROM axon.domain_event_entry"
                + " WHERE aggregate_identifier = ? ORDER BY sequence_number",
            group.toString());
    assertThat(stored).hasSize(5);
    for (var row : stored) {
      var payload = new String((byte[]) row.get("payload"), StandardCharsets.UTF_8);
      var metadata =
          row.get("meta_data") == null
              ? ""
              : new String((byte[]) row.get("meta_data"), StandardCharsets.UTF_8);
      assertThat(payload)
          .doesNotContain(
              "Create reason", "Add reason", "Lead reason", "Remove reason", "Dissolve reason");
      assertThat(metadata)
          .doesNotContain(
              "Create reason", "Add reason", "Lead reason", "Remove reason", "Dissolve reason");
    }

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              jdbcTemplate.update(
                  "DELETE FROM axon.application_history WHERE justification_id IN"
                      + " (SELECT id FROM axon.linked_group_justification WHERE group_id = ?)",
                  group);
              stored.forEach(this::replayIntoHistoryProjection);
              stored.forEach(this::replayIntoHistoryProjection);
            });
    assertThat(payloadCount(group)).isEqualTo(5);
    assertSharedReference(group, "Create reason", 2);
    assertSharedReference(group, "Add reason", 3);
    assertSharedReference(group, "Lead reason", 3);
    assertSharedReference(group, "Remove reason", 3);
    assertSharedReference(group, "Dissolve reason", 2);
    assertHistory(member, DomainEventType.APPLICATION_GROUP_DISSOLVED, "Dissolve reason");
  }

  @Test
  void givenNoEventHistory_whenActionsChangeState_thenNoDescriptionIsStored() {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    assertThat(linkWithReason(member, lead, null, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_CREATED, null);
    assertHistory(member, DomainEventType.APPLICATION_GROUP_JOINED, null);
    assertThat(payloadCount(group)).isZero();

    assertThat(change(member, "make-lead", group, 0, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_LEAD_CHANGED, null);
    assertHistory(member, DomainEventType.APPLICATION_GROUP_LEAD_CHANGED, null);
    assertThat(change(lead, "unlink", group, 1, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_LEFT, null);
    assertHistory(member, DomainEventType.APPLICATION_GROUP_DISSOLVED, null);
    assertThat(payloadCount(group)).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    "link-lead, false",
    "link-lead, true",
    "link-member, false",
    "link-member, true",
    "make-lead, false",
    "make-lead, true"
  })
  void givenNoOp_whenRepeated_thenNoContentAndAllPersistedHistoryIsUnchanged(
      String operation, boolean suppliedHistory) {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    var joining = createApplication(surname);
    assertThat(linkWithReason(member, lead, null, "Original creation").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(
            linkWithReason(joining, lead, groupToken(group, 0), "Original addition")
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertSharedReference(group, "Original creation", 2);
    assertSharedReference(group, "Original addition", 3);
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_CREATED, "Original creation");
    assertHistory(member, DomainEventType.APPLICATION_GROUP_JOINED, "Original creation");
    assertHistory(lead, DomainEventType.APPLICATION_GROUP_MEMBER_ADDED, "Original addition");
    assertHistory(member, DomainEventType.APPLICATION_GROUP_MEMBER_ADDED, "Original addition");
    assertHistory(joining, DomainEventType.APPLICATION_GROUP_JOINED, "Original addition");

    var applications = List.of(lead, member, joining);
    var before = noOpSnapshot(group, applications);
    assertThat(before.events()).isEqualTo(2);
    assertThat(before.payloads()).isEqualTo(2);
    assertNoOpSnapshotUnchanged(group, applications, before);

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("linkedGroupVersion", groupToken(group, 1));
    boolean makeLead = operation.equals("make-lead");
    if (!makeLead) {
      request.put("applicationId", operation.equals("link-lead") ? lead : member);
      request.put("linkType", ApplicationLinkType.FAMILY.getValue());
    }
    if (suppliedHistory) {
      request.put("eventHistory", Map.of("eventDescription", "Discard no-op description"));
    }
    var endpoint =
        "/api/v0/applications/"
            + (makeLead ? lead : joining)
            + "/"
            + (makeLead ? "make-lead" : "link");
    for (int attempt = 0; attempt < 2; attempt++) {
      var response =
          restTemplate.exchange(
              url(endpoint), HttpMethod.POST, new HttpEntity<>(request, headers()), String.class);
      assertThat(response.getStatusCode())
          .as("attempt %s", attempt + 1)
          .isEqualTo(HttpStatus.NO_CONTENT);
      assertNoOpSnapshotUnchanged(group, applications, before);
    }
  }

  private NoOpSnapshot noOpSnapshot(UUID group, List<UUID> applications) {
    Map<UUID, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    Map<UUID, Map<String, Long>> counts = new LinkedHashMap<>();
    for (var application : applications) {
      var history =
          jdbcTemplate.queryForList(
              "SELECT * FROM axon.application_history WHERE application_id = ? ORDER BY event_id",
              application);
      rows.put(application, history);
      Map<String, Long> perType = new LinkedHashMap<>();
      for (var type : DomainEventType.values()) {
        perType.put(type.getValue(), 0L);
      }
      history.forEach(row -> perType.merge((String) row.get("event_type"), 1L, Long::sum));
      counts.put(application, perType);
    }
    return new NoOpSnapshot(rows, counts, groupEventCount(group), payloadCount(group));
  }

  private void assertNoOpSnapshotUnchanged(
      UUID group, List<UUID> applications, NoOpSnapshot before) {
    assertNoOpSnapshotEquals(group, applications, before);
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertNoOpSnapshotEquals(group, applications, before));
  }

  private void assertNoOpSnapshotEquals(UUID group, List<UUID> applications, NoOpSnapshot before) {
    var after = noOpSnapshot(group, applications);
    assertThat(after.events()).as("linked-group events").isEqualTo(before.events());
    assertThat(after.payloads()).as("justification payloads").isEqualTo(before.payloads());
    for (var application : applications) {
      assertThat(after.rows().get(application))
          .as("total history for %s", application)
          .hasSize(before.rows().get(application).size());
      assertThat(after.counts().get(application))
          .as("per-type history for %s", application)
          .isEqualTo(before.counts().get(application));
      assertThat(after.rows().get(application))
          .as("exact history rows for %s", application)
          .isEqualTo(before.rows().get(application));
    }
  }

  private record NoOpSnapshot(
      Map<UUID, List<Map<String, Object>>> rows,
      Map<UUID, Map<String, Long>> counts,
      long events,
      long payloads) {}

  @ParameterizedTest
  @EnumSource(Action.class)
  void givenEventAppendFailure_whenActionAttempted_thenPayloadAndEventsRollBack(Action action) {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    assertThat(link(member, lead, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);
    var joining = createApplication(surname);
    if (action == Action.REMOVE) {
      assertThat(link(joining, lead, groupToken(group, 0)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    var before =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            Long.class,
            group.toString());
    rejectEventsFor(group);
    ResponseEntity<String> response =
        switch (action) {
          case ADD -> linkWithReason(joining, lead, groupToken(group, 0), "Rollback reason");
          case LEAD -> change(member, "make-lead", group, 0, "Rollback reason");
          case REMOVE -> change(joining, "unlink", group, 1, "Rollback reason");
          case DISSOLVE -> change(member, "unlink", group, 0, "Rollback reason");
        };
    assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    assertThat(payloadCount(group)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
                Long.class,
                group.toString()))
        .isEqualTo(before);
  }

  @Test
  void givenRejectedCreate_whenAttempted_thenJustificationRollsBack() {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    jdbcTemplate.execute(
        """
        CREATE FUNCTION axon.reject_test_justification_create() RETURNS trigger AS $$
        BEGIN
          IF NEW.payload_type LIKE '%LinkedApplicationGroupCreatedEvent' THEN
            RAISE EXCEPTION 'forced group creation failure';
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_test_justification_create BEFORE INSERT ON axon.domain_event_entry
        FOR EACH ROW EXECUTE FUNCTION axon.reject_test_justification_create()
        """);
    try {
      var before =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM axon.linked_group_justification", Long.class);
      assertThat(
              linkWithReason(member, lead, null, "Rollback create")
                  .getStatusCode()
                  .is5xxServerError())
          .isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.linked_group_justification", Long.class))
          .isEqualTo(before);
      awaitRoute(lead, ApplicationGroupRouteKind.STANDALONE);
      awaitRoute(member, ApplicationGroupRouteKind.STANDALONE);
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER reject_test_justification_create ON axon.domain_event_entry");
      jdbcTemplate.execute("DROP FUNCTION axon.reject_test_justification_create()");
    }
  }

  private ResponseEntity<String> linkWithReason(
      UUID source, UUID target, String token, String text) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + source + "/link"),
        HttpMethod.POST,
        new HttpEntity<>(
            requestBody(
                new ApplicationLinkRequest(target, ApplicationLinkType.FAMILY)
                    .linkedGroupVersion(token)
                    .eventHistory(
                        text == null ? null : new EventHistoryRequest().eventDescription(text)),
                text),
            headers()),
        String.class);
  }

  private ResponseEntity<String> change(
      UUID application, String operation, UUID group, long version, String text) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + application + "/" + operation),
        HttpMethod.POST,
        new HttpEntity<>(
            requestBody(
                new LinkedGroupChangeRequest(groupToken(group, version))
                    .eventHistory(
                        text == null ? null : new EventHistoryRequest().eventDescription(text)),
                text),
            headers()),
        String.class);
  }

  private Map<String, Object> requestBody(Object request, String text) {
    var body = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
    if (text == null) {
      body.remove("eventHistory");
    }
    return body;
  }

  private long payloadCount(UUID group) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.linked_group_justification WHERE group_id = ?",
        Long.class,
        group);
  }

  @Test
  void givenStoredJustification_whenUpdatedOrDeleted_thenDatabaseRejectsMutation() {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    assertThat(linkWithReason(member, lead, null, "Immutable reason").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE axon.linked_group_justification SET justification = 'changed'"
                        + " WHERE group_id = ?",
                    group))
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM axon.linked_group_justification WHERE group_id = ?", group))
        .hasMessageContaining("append-only");
    assertSharedReference(group, "Immutable reason", 2);
  }

  @ParameterizedTest
  @EnumSource(Action.class)
  void givenJustificationWriteFailure_whenActionAttempted_thenEventsAndHistoryRollBack(
      Action action) {
    var surname = uniqueLastName();
    var lead = createApplication(surname);
    var member = createApplication(surname);
    assertThat(link(member, lead, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var group = awaitRoute(lead, ApplicationGroupRouteKind.LINKED_GROUP);
    var joining = createApplication(surname);
    if (action == Action.REMOVE) {
      assertThat(link(joining, lead, groupToken(group, 0)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    var eventsBefore = groupEventCount(group);
    var payloadsBefore = payloadCount(group);
    var historyWithTextBefore = historyRowsWithJustification();
    jdbcTemplate.execute(
        """
        CREATE FUNCTION axon.reject_test_justification_write() RETURNS trigger AS $$
        BEGIN
          RAISE EXCEPTION 'forced justification write failure';
        END;
        $$ LANGUAGE plpgsql
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_test_justification_write BEFORE INSERT ON axon.linked_group_justification
        FOR EACH ROW EXECUTE FUNCTION axon.reject_test_justification_write()
        """);
    try {
      ResponseEntity<String> response =
          switch (action) {
            case ADD -> linkWithReason(joining, lead, groupToken(group, 0), "Lost reason");
            case LEAD -> change(member, "make-lead", group, 0, "Lost reason");
            case REMOVE -> change(joining, "unlink", group, 1, "Lost reason");
            case DISSOLVE -> change(member, "unlink", group, 0, "Lost reason");
          };
      assertThat(response.getStatusCode().is5xxServerError()).isTrue();
      assertThat(payloadCount(group)).isEqualTo(payloadsBefore);
      assertThat(groupEventCount(group)).isEqualTo(eventsBefore);
      assertThat(historyRowsWithJustification()).isEqualTo(historyWithTextBefore);
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER reject_test_justification_write ON axon.linked_group_justification");
      jdbcTemplate.execute("DROP FUNCTION axon.reject_test_justification_write()");
    }
  }

  private long groupEventCount(UUID group) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
        Long.class,
        group.toString());
  }

  private long historyRowsWithJustification() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_history WHERE justification_id IS NOT NULL",
        Long.class);
  }

  private void assertHistory(UUID application, DomainEventType type, String text) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var response =
                  restTemplate.exchange(
                      url(
                          "/api/v0/applications/"
                              + application
                              + "/history-search?eventType="
                              + type.getValue()),
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      ApplicationHistoryResponse.class);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody()).isNotNull();
              assertThat(response.getBody().getEvents())
                  .anySatisfy(
                      event -> {
                        assertThat(event.getDomainEventType()).isEqualTo(type);
                        assertThat(event.getEventDescription()).isEqualTo(text);
                      });
            });
  }

  private void assertSharedReference(UUID group, String text, int expectedRows) {
    var payloads =
        jdbcTemplate.queryForList(
            "SELECT id FROM axon.linked_group_justification WHERE group_id = ? AND justification = ?",
            UUID.class,
            group,
            text);
    assertThat(payloads).hasSize(1);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM axon.application_history WHERE justification_id = ?",
                            Integer.class,
                            payloads.getFirst()))
                    .isEqualTo(expectedRows));
  }

  /**
   * Feeds stored group events straight into the history projection; this is not a processor
   * rebuild.
   */
  private void replayIntoHistoryProjection(Map<String, Object> row) {
    String type = (String) row.get("payload_type");
    byte[] payload = (byte[]) row.get("payload");
    Object event =
        switch (type.substring(type.lastIndexOf('.') + 1)) {
          case "LinkedApplicationGroupCreatedEvent" ->
              objectMapper.readValue(payload, LinkedApplicationGroupCreatedEvent.class);
          case "MemberAddedToGroupEvent" ->
              objectMapper.readValue(payload, MemberAddedToGroupEvent.class);
          case "LinkedApplicationGroupLeadChangedEvent" ->
              objectMapper.readValue(payload, LinkedApplicationGroupLeadChangedEvent.class);
          case "MemberRemovedFromGroupEvent" ->
              objectMapper.readValue(payload, MemberRemovedFromGroupEvent.class);
          case "LinkedApplicationGroupDissolvedEvent" ->
              objectMapper.readValue(payload, LinkedApplicationGroupDissolvedEvent.class);
          default -> throw new IllegalArgumentException("Unexpected group event type " + type);
        };
    var message =
        new GenericEventMessage(
            (String) row.get("event_identifier"),
            new MessageType(event.getClass()),
            event,
            Map.of(),
            Instant.EPOCH);
    switch (event) {
      case LinkedApplicationGroupCreatedEvent created -> historyProjection.on(created, message);
      case MemberAddedToGroupEvent added -> historyProjection.on(added, message);
      case LinkedApplicationGroupLeadChangedEvent lead -> historyProjection.on(lead, message);
      case MemberRemovedFromGroupEvent removed -> historyProjection.on(removed, message);
      case LinkedApplicationGroupDissolvedEvent dissolved ->
          historyProjection.on(dissolved, message);
      default -> throw new IllegalArgumentException("Unexpected group event " + type);
    }
  }

  private enum Action {
    ADD,
    LEAD,
    REMOVE,
    DISSOLVE
  }
}
