package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.ReadsEventContent;

/**
 * An event source that rejoins content for each streamed event, failing if an event whose type has
 * content fields has no content row. Only the processor of a handler annotated {@link
 * ReadsEventContent} may have one; see {@link #forHandler}.
 */
public final class ContentReadingEventSource implements StreamableEventSource {

  private final StreamableEventSource delegate;
  private final Supplier<EventContentSeparator> separator;
  private final Supplier<EventContentStore> store;
  private final Supplier<EventConverter> converter;

  private ContentReadingEventSource(
      StreamableEventSource delegate,
      Supplier<EventContentSeparator> separator,
      Supplier<EventContentStore> store,
      Supplier<EventConverter> converter) {
    this.delegate = delegate;
    this.separator = separator;
    this.store = store;
    this.converter = converter;
  }

  /**
   * Wraps the event source of a handler's processor so it receives content.
   *
   * @throws IllegalStateException if the handler is not annotated {@link ReadsEventContent}
   */
  public static StreamableEventSource forHandler(
      Class<?> handlerType,
      StreamableEventSource delegate,
      Supplier<EventContentSeparator> separator,
      Supplier<EventContentStore> store,
      Supplier<EventConverter> converter) {
    if (!handlerType.isAnnotationPresent(ReadsEventContent.class)) {
      throw new IllegalStateException(
          handlerType.getName() + " is not annotated @ReadsEventContent");
    }
    return new ContentReadingEventSource(delegate, separator, store, converter);
  }

  @Override
  public MessageStream<EventMessage> open(StreamingCondition condition, ProcessingContext context) {
    return delegate.open(condition, context).mapMessage(this::join);
  }

  private EventMessage join(EventMessage message) {
    if (message.payload() == null) {
      return message;
    }
    ObjectNode content =
        store.get().findByEventIds(List.of(message.identifier())).get(message.identifier());
    if (content == null) {
      separator.get().requireNoContentFields(message.type().name(), message.identifier());
      return message;
    }
    return ContentJoin.join(message, content, separator, converter);
  }

  @Override
  public CompletableFuture<TrackingToken> firstToken(ProcessingContext context) {
    return delegate.firstToken(context);
  }

  @Override
  public CompletableFuture<TrackingToken> latestToken(ProcessingContext context) {
    return delegate.latestToken(context);
  }

  @Override
  public CompletableFuture<TrackingToken> tokenAt(Instant at, ProcessingContext context) {
    return delegate.tokenAt(at, context);
  }
}
