package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator.KeptFieldViolationException;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator.UnclassifiedEventException;

class EventContentSeparatorTest {

  private static final String ASSIGNED = "access.WorkItemAssigned";
  private static final String MARKED = "test.Marked";
  private static final String NON_RECORD = "test.NonRecord";

  private final EventJson json = new EventJson();
  private final EventContentSeparator separator =
      new EventContentSeparator(
          json,
          Map.of(
              ContentTestEvent.TYPE,
              ContentTestEvent.class,
              ASSIGNED,
              WorkItemAssigned.class,
              MARKED,
              MarkedTestEvent.class,
              NON_RECORD,
              NonRecordTestEvent.class));

  record MarkedTestEvent(@RetainedField String id, @RetainedField int count, String note) {}

  static final class NonRecordTestEvent {}

  @Test
  void givenPayload_whenSeparated_thenUnlistedFieldsAreContentAndRejoinRestoresThem() {
    String id = UUID.randomUUID().toString();
    ObjectNode payload = json.mapper().createObjectNode().put("id", id).put("note", "secret");

    var split = separator.separate(ContentTestEvent.TYPE, payload);

    assertThat(split.kept()).isEqualTo(json.mapper().createObjectNode().put("id", id));
    assertThat(split.content()).isEqualTo(json.mapper().createObjectNode().put("note", "secret"));
    assertThat(separator.rejoin(split.kept(), split.content())).isEqualTo(payload);
  }

  @Test
  void givenRetainedFieldMarks_whenSeparated_thenOnlyMarkedComponentsAreKept() {
    ObjectNode payload =
        json.mapper().createObjectNode().put("id", "a").put("count", 2).put("note", "secret");

    var split = separator.separate(MARKED, payload);

    assertThat(split.kept())
        .isEqualTo(json.mapper().createObjectNode().put("id", "a").put("count", 2));
    assertThat(split.content()).isEqualTo(json.mapper().createObjectNode().put("note", "secret"));
  }

  @Test
  void givenFieldWithoutComponent_whenSeparated_thenItIsContent() {
    ObjectNode payload = json.mapper().createObjectNode().put("id", "a").put("extra", "secret");

    var split = separator.separate(MARKED, payload);

    assertThat(split.kept()).isEqualTo(json.mapper().createObjectNode().put("id", "a"));
    assertThat(split.content()).isEqualTo(json.mapper().createObjectNode().put("extra", "secret"));
  }

  @Test
  void givenEvent_whenErased_thenContentReadsAsErased() {
    var event = new ContentTestEvent(UUID.randomUUID().toString(), Erasable.of("secret"));

    var erased = separator.erase(ContentTestEvent.TYPE, event);

    assertThat(erased.id()).isEqualTo(event.id());
    assertThat(erased.note().isErased()).isTrue();
  }

  @Test
  void givenTypeWithContentFields_whenContentRowMissing_thenFails() {
    assertThat(separator.hasContentFields(ContentTestEvent.TYPE)).isTrue();
    assertThatThrownBy(() -> separator.requireNoContentFields(ContentTestEvent.TYPE, "event-1"))
        .isInstanceOf(MissingEventContentException.class)
        .hasMessage("Event event-1 of type test.Content has no content row");
  }

  @Test
  void givenTypeWithOnlyKeptFields_whenContentRowMissing_thenAccepted() {
    assertThat(separator.hasContentFields(ASSIGNED)).isFalse();
    separator.requireNoContentFields(ASSIGNED, "event-2");
  }

  @Test
  void givenTypeOutsideTheEventClasses_whenContentRowMissing_thenAccepted() {
    var withoutClasses = new EventContentSeparator(json, Map.of());

    assertThat(withoutClasses.hasContentFields(ContentTestEvent.TYPE)).isFalse();
    assertThat(withoutClasses.hasContentFields("test.Missing")).isFalse();
  }

  @Test
  void givenUnknownType_whenSeparated_thenRejected() {
    assertThatThrownBy(() -> separator.separate("test.Missing", json.mapper().createObjectNode()))
        .isInstanceOf(UnclassifiedEventException.class)
        .hasMessage("Unknown event type test.Missing");
  }

  @Test
  void givenNonRecordType_whenSeparated_thenRejected() {
    assertThatThrownBy(() -> separator.separate(NON_RECORD, json.mapper().createObjectNode()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Event test.NonRecord must be a record");
    assertThatThrownBy(() -> separator.hasContentFields(NON_RECORD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Event test.NonRecord must be a record");
  }

  @Test
  void givenMetadata_whenChecked_thenOnlyAllowedKeysPass() {
    separator.checkMetadata(ContentTestEvent.TYPE, EventContentSeparator.ALLOWED_METADATA_KEYS);

    assertThatThrownBy(
            () -> separator.checkMetadata(ContentTestEvent.TYPE, Set.of("correlationId", "email")))
        .isInstanceOf(KeptFieldViolationException.class)
        .hasMessage(
            "Event test.Content keeps fields that are not allowed: "
                + "[metadata keys not allowed: [email]]");
  }
}
