package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.dstew.access.eventcontent.ContentTestEvent;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;
import uk.gov.justice.laa.dstew.access.eventcontent.MissingEventContentException;
import uk.gov.justice.laa.dstew.access.eventcontent.ReadsEventContent;

@ExtendWith(MockitoExtension.class)
class ContentReadingEventSourceTest {

  private final EventJson json = new EventJson();
  private final EventConverter converter =
      new DelegatingEventConverter(new JacksonConverter(json.mapper()));
  private final EventContentSeparator separator =
      new EventContentSeparator(json, Map.of(ContentTestEvent.TYPE, ContentTestEvent.class));

  @Mock private StreamableEventSource delegate;
  @Mock private EventContentStore store;
  @Mock private TrackingToken trackingToken;

  private StreamableEventSource source;

  @BeforeEach
  void setUp() {
    source =
        ContentReadingEventSource.forHandler(
            ContentReader.class, delegate, () -> separator, () -> store, () -> converter);
  }

  @Test
  void givenHandlerNotReadingContent_whenWrapped_thenRejected() {
    assertThatThrownBy(
            () ->
                ContentReadingEventSource.forHandler(
                    OtherHandler.class, delegate, () -> separator, () -> store, () -> converter))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(OtherHandler.class.getName() + " is not annotated @ReadsEventContent");
  }

  @Test
  void givenStreamedEvents_whenOpened_thenContentIsRejoinedWhereItExists() {
    EventMessage withContent = message(json.mapper().createObjectNode().put("id", "1"));
    EventMessage erased = message(json.mapper().createObjectNode().put("id", "2"));
    EventMessage empty = message(null);
    when(store.findByEventId(withContent.identifier()))
        .thenReturn(Optional.of(json.mapper().createObjectNode().put("note", "secret")));
    when(store.findByEventId(erased.identifier()))
        .thenReturn(Optional.of(json.mapper().createObjectNode()));
    StreamingCondition condition = StreamingCondition.startingFrom(null);
    when(delegate.open(condition, null))
        .thenReturn(MessageStream.fromItems(withContent, erased, empty));

    MessageStream<EventMessage> stream = source.open(condition, null);

    assertThat(payload(stream.next().orElseThrow().message()).get("note").asString())
        .isEqualTo("secret");
    assertThat(payload(stream.next().orElseThrow().message()).has("note")).isFalse();
    assertThat(stream.next().orElseThrow().message().payload()).isNull();
  }

  @Test
  void givenEventWithContentFieldsAndNoContentRow_whenOpened_thenFails() {
    EventMessage lost = message(json.mapper().createObjectNode().put("id", "3"));
    when(store.findByEventId(lost.identifier())).thenReturn(Optional.empty());
    StreamingCondition condition = StreamingCondition.startingFrom(null);
    when(delegate.open(condition, null)).thenReturn(MessageStream.fromItems(lost));

    MessageStream<EventMessage> stream = source.open(condition, null);

    assertThatThrownBy(stream::next)
        .isInstanceOf(MissingEventContentException.class)
        .hasMessageContaining(lost.identifier());
  }

  @Test
  void givenTokenRequests_whenMade_thenDelegated() {
    CompletableFuture<TrackingToken> token = CompletableFuture.completedFuture(trackingToken);
    Instant at = Instant.now();
    when(delegate.firstToken(null)).thenReturn(token);
    when(delegate.latestToken(null)).thenReturn(token);
    when(delegate.tokenAt(at, null)).thenReturn(token);

    assertThat(source.firstToken(null)).isSameAs(token);
    assertThat(source.latestToken(null)).isSameAs(token);
    assertThat(source.tokenAt(at, null)).isSameAs(token);
  }

  private EventMessage message(Object payload) {
    return new GenericEventMessage(
        UUID.randomUUID().toString(),
        new MessageType(ContentTestEvent.TYPE),
        payload == null ? null : converter.convert(payload, byte[].class),
        Map.of(),
        Instant.now());
  }

  private JsonNode payload(EventMessage message) {
    return converter.convert(message.payload(), JsonNode.class);
  }

  @ReadsEventContent
  private static final class ContentReader {}

  private static final class OtherHandler {}
}
