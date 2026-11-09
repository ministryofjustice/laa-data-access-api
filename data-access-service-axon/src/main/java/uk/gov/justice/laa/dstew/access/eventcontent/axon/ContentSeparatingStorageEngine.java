package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore.ContentRow;

/**
 * Wraps Axon's storage engine. On append it keeps only schema-listed fields on the stored event and
 * writes everything else to the content store; on sourcing it rejoins content so aggregates load
 * whole. The event stream used by processors is passed through unchanged, so processors receive
 * kept fields only unless they are registered content readers.
 */
final class ContentSeparatingStorageEngine implements EventStorageEngine {

  private final EventStorageEngine delegate;
  private final Supplier<EventContentSeparator> separator;
  private final Supplier<EventContentStore> store;
  private final Supplier<EventConverter> converter;

  ContentSeparatingStorageEngine(
      EventStorageEngine delegate,
      Supplier<EventContentSeparator> separator,
      Supplier<EventContentStore> store,
      Supplier<EventConverter> converter) {
    this.delegate = delegate;
    this.separator = separator;
    this.store = store;
    this.converter = converter;
  }

  @Override
  public CompletableFuture<AppendTransaction<?>> appendEvents(
      AppendCondition condition, ProcessingContext context, List<TaggedEventMessage<?>> events) {
    List<TaggedEventMessage<?>> separated = new ArrayList<>();
    List<ContentRow> contentRows = new ArrayList<>();
    try {
      for (TaggedEventMessage<?> tagged : events) {
        separated.add(separate(tagged, contentRows));
      }
      // Inside the append's database transaction, so content and event commit or roll back
      // together. AppendTransaction.commit() runs after the database commit, so never write there.
      if (!contentRows.isEmpty()) {
        store.get().save(contentRows);
      }
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
    return delegate.appendEvents(condition, context, separated);
  }

  private TaggedEventMessage<?> separate(TaggedEventMessage<?> tagged, List<ContentRow> rows) {
    EventMessage event = tagged.event();
    String typeName = event.type().name();
    EventContentSeparator rules = separator.get();
    rules.checkMetadata(typeName, event.metadata().keySet());
    EventConverter axon = converter.get();
    EventContentSeparator.Split split =
        rules.separate(typeName, (ObjectNode) axon.convert(event.payload(), JsonNode.class));
    if (!split.content().isEmpty()) {
      rows.add(new ContentRow(event.identifier(), tagKeys(tagged.tags()), split.content()));
    }
    EventMessage kept =
        new GenericEventMessage(
            event.identifier(),
            event.type(),
            axon.convert(split.kept(), byte[].class),
            event.metadata(),
            event.timestamp());
    return new GenericTaggedEventMessage<>(kept, tagged.tags());
  }

  @Override
  public MessageStream<EventMessage> source(
      SourcingCondition condition, ProcessingContext context) {
    Set<String> tags =
        condition.criteria().flatten().stream()
            .map(EventCriterion::tags)
            .flatMap(Set::stream)
            .map(ContentSeparatingStorageEngine::tagKey)
            .collect(Collectors.toSet());
    Map<String, ObjectNode> content = store.get().findByTags(tags);
    return delegate.source(condition, context).mapMessage(rejoin(content));
  }

  private Function<EventMessage, EventMessage> rejoin(Map<String, ObjectNode> content) {
    return message -> {
      if (message == null || message.payload() == null) {
        return message;
      }
      ObjectNode extra = content.get(message.identifier());
      if (extra == null) {
        String typeName = message.type().name();
        if (!separator.get().hasContentFields(typeName)) {
          return message;
        }
        // Content was read by tag before the events, so an event committed in between is found
        // here. Content commits with its event and erasure keeps the row, so none means lost.
        extra =
            store
                .get()
                .findByEventId(message.identifier())
                .orElseThrow(
                    () ->
                        new EventContentSeparator.MissingEventContentException(
                            typeName, message.identifier()));
      }
      return ContentJoin.join(message, extra, separator, converter);
    };
  }

  @Override
  public MessageStream<EventMessage> stream(StreamingCondition condition) {
    return delegate.stream(condition);
  }

  @Override
  public CompletableFuture<TrackingToken> firstToken() {
    return delegate.firstToken();
  }

  @Override
  public CompletableFuture<TrackingToken> latestToken() {
    return delegate.latestToken();
  }

  @Override
  public CompletableFuture<TrackingToken> tokenAt(Instant at) {
    return delegate.tokenAt(at);
  }

  @Override
  public void describeTo(ComponentDescriptor descriptor) {
    descriptor.describeWrapperOf(delegate);
  }

  static Set<String> tagKeys(Set<Tag> tags) {
    return tags.stream().map(ContentSeparatingStorageEngine::tagKey).collect(Collectors.toSet());
  }

  static String tagKey(Tag tag) {
    return tag.key() + "=" + tag.value();
  }
}
