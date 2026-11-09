package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.RegisteredContentReaders;

/**
 * An event source that rejoins content for each streamed event. Only registered content readers may
 * have one; see {@link #forProcessor}.
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
   * Wraps a processor's event source so it receives content.
   *
   * @throws IllegalStateException if the processor is not a registered content reader
   */
  public static StreamableEventSource forProcessor(
      String processorName,
      StreamableEventSource delegate,
      Supplier<EventContentSeparator> separator,
      Supplier<EventContentStore> store,
      Supplier<EventConverter> converter) {
    RegisteredContentReaders.require(processorName);
    return new ContentReadingEventSource(delegate, separator, store, converter);
  }

  @Override
  public MessageStream<EventMessage> open(StreamingCondition condition, ProcessingContext context) {
    return delegate.open(condition, context).mapMessage(this::join);
  }

  private EventMessage join(EventMessage message) {
    if (message == null || message.payload() == null) {
      return message;
    }
    return store
        .get()
        .findByEventId(message.identifier())
        .map(content -> ContentJoin.join(message, content, separator, converter))
        .orElse(message);
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
