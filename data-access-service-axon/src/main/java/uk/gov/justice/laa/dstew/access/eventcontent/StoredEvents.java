package uk.gov.justice.laa.dstew.access.eventcontent;

import java.util.Collection;
import java.util.Map;
import org.axonframework.messaging.eventhandling.EventMessage;

/**
 * Port for loading stored events by identifier, with their content rejoined. Erased content loads
 * as erased. Only registered content readers may load.
 */
public interface StoredEvents {

  /**
   * Loads events by identifier; identifiers with no stored event are absent from the result.
   *
   * @param readerName the registered content reader loading the events
   * @throws IllegalStateException if the reader is not registered
   */
  Map<String, EventMessage> load(String readerName, Collection<String> identifiers);
}
