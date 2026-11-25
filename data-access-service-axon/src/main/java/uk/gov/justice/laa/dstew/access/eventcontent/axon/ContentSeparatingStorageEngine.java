package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageStream.Entry;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore.ContentRow;
import uk.gov.justice.laa.dstew.access.eventcontent.MissingEventContentException;

/**
 * Wraps Axon's storage engine. On append it keeps only retained fields on the stored event and
 * writes everything else to the content store; on sourcing it reads the events, then their content
 * by event id in one lookup, and rejoins it so aggregates load whole, failing if an event whose
 * type has content fields has no content row. The event stream used by processors is passed through
 * unchanged, so processors receive retained fields only unless their projection is annotated
 * {@code @ReadsEventContent}.
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
      rows.add(new ContentRow(event.identifier(), split.content()));
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

  /**
   * Reads every entry first, then the content of the content-bearing events in one lookup. Whole
   * entries are kept, not just messages: the last entry carries no event, and its context holds the
   * consistency marker the event store needs for the next append.
   */
  @Override
  public MessageStream<EventMessage> source(
      SourcingCondition condition, ProcessingContext context) {
    CompletableFuture<MessageStream<EventMessage>> rejoined =
        delegate
            .source(condition, context)
            .reduce(
                new ArrayList<Entry<EventMessage>>(),
                (entries, entry) -> {
                  entries.add(entry);
                  return entries;
                })
            .thenApply(this::rejoin);
    return DelayedMessageStream.create(rejoined);
  }

  private MessageStream<EventMessage> rejoin(List<Entry<EventMessage>> entries) {
    List<String> eventIds =
        entries.stream()
            .map(Entry::message)
            .filter(this::hasContent)
            .map(EventMessage::identifier)
            .toList();
    Map<String, ObjectNode> content =
        eventIds.isEmpty() ? Map.of() : store.get().findByEventIds(eventIds);
    List<Entry<EventMessage>> joined =
        entries.stream().map(entry -> entry.map(message -> join(message, content))).toList();
    return MessageStream.fromStream(joined.stream(), Entry::message, entry -> entry);
  }

  private boolean hasContent(@Nullable EventMessage message) {
    return message != null
        && message.payload() != null
        && separator.get().hasContentFields(message.type().name());
  }

  private EventMessage join(EventMessage message, Map<String, ObjectNode> content) {
    if (!hasContent(message)) {
      return message;
    }
    ObjectNode extra = content.get(message.identifier());
    if (extra == null) {
      // Content commits with its event and was read after it, and erasure keeps the row, so an
      // event without one has lost its content.
      throw new MissingEventContentException(message.type().name(), message.identifier());
    }
    return ContentJoin.join(message, extra, separator, converter);
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
}
