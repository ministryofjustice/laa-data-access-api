package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator.KeptFieldViolationException;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator.UnclassifiedEventException;

class EventContentSeparatorTest {

  private final EventJson json = new EventJson();
  private final EventContentSeparator separator = new EventContentSeparator(json);

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
  void givenEvent_whenErased_thenContentReadsAsErased() {
    var event = new ContentTestEvent(UUID.randomUUID().toString(), Erasable.of("secret"));

    var erased = separator.erase(ContentTestEvent.TYPE, event);

    assertThat(erased.id()).isEqualTo(event.id());
    assertThat(erased.note().isErased()).isTrue();
  }

  @Test
  void givenKeptFieldBreakingSchema_whenSeparated_thenRejected() {
    ObjectNode payload = json.mapper().createObjectNode().put("id", "not-a-uuid");

    assertThatThrownBy(() -> separator.separate(ContentTestEvent.TYPE, payload))
        .isInstanceOf(KeptFieldViolationException.class)
        .hasMessageContaining("Kept fields of test.Content violate schema");
  }

  @Test
  void givenUnclassifiedType_whenSeparated_thenRejected() {
    assertThat(separator.isClassified("test.Missing")).isFalse();
    assertThat(separator.isClassified(ContentTestEvent.TYPE)).isTrue();
    assertThatThrownBy(() -> separator.separate("test.Missing", json.mapper().createObjectNode()))
        .isInstanceOf(UnclassifiedEventException.class)
        .hasMessage("No kept-field schema for event type test.Missing");
  }

  @Test
  void givenMetadata_whenChecked_thenOnlyAllowedKeysPass() {
    separator.checkMetadata(ContentTestEvent.TYPE, EventContentSeparator.ALLOWED_METADATA_KEYS);

    assertThatThrownBy(
            () -> separator.checkMetadata(ContentTestEvent.TYPE, Set.of("correlationId", "email")))
        .isInstanceOf(KeptFieldViolationException.class)
        .hasMessageContaining("metadata keys not allowed: [email]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "test.AdditionalAllowed",
        "test.AdditionalUnset",
        "test.AdditionalSchema",
        "test.PropertiesUnset",
        "test.PropertiesNotObject"
      })
  void givenSchemaThatDoesNotCloseItsKeptFields_whenLoaded_thenRejected(String typeName) {
    assertThatThrownBy(() -> separator.isClassified(typeName))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(EventContentSeparator.SCHEMA_ROOT + typeName + ".json must");
  }
}
