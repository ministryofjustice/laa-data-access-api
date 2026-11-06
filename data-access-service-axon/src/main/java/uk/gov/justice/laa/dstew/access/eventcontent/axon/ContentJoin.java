package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import java.util.function.Supplier;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;

/** Rebuilds a stored event message with its content rejoined. */
final class ContentJoin {

  private ContentJoin() {}

  static EventMessage join(
      EventMessage message,
      ObjectNode content,
      Supplier<EventContentSeparator> separator,
      Supplier<EventConverter> converter) {
    EventConverter axon = converter.get();
    ObjectNode kept = (ObjectNode) axon.convert(message.payload(), JsonNode.class);
    ObjectNode whole = separator.get().rejoin(kept, content);
    return new GenericEventMessage(
        message.identifier(),
        message.type(),
        axon.convert(whole, byte[].class),
        message.metadata(),
        message.timestamp());
  }
}
