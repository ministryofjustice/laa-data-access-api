package uk.gov.justice.laa.dstew.access.replay;

import java.util.List;
import java.util.Map;

/**
 * Replays raw {@code axon.domain_event_entry} rows (as returned by a plain JDBC query) into an
 * arbitrary state object, dispatching on the persisted {@code payload_type} column.
 *
 * <p>This allows a projection's current state to be reconstructed directly from the event store,
 * without going through any Axon API — useful both as a contract-test verification tool and as a
 * diagnostic/recovery mechanism.
 */
public final class RawEventReplayer {

  private RawEventReplayer() {}

  /** Applies a single event's raw payload bytes onto the given state. */
  @FunctionalInterface
  public interface EventApplier<S> {
    void apply(S state, byte[] payload) throws Exception;
  }

  /**
   * Folds the supplied rows into {@code state}, in order, using the applier registered for each
   * row's {@code payload_type}.
   *
   * @param rows rows containing {@code payload} and {@code payload_type} columns, in the order they
   *     should be applied
   * @param state the mutable state object to fold events into
   * @param dispatchers appliers keyed by fully-qualified event class name
   * @throws IllegalArgumentException if a row's {@code payload_type} has no registered applier
   */
  public static <S> void replay(
      List<Map<String, Object>> rows, S state, Map<String, EventApplier<S>> dispatchers)
      throws Exception {
    for (Map<String, Object> row : rows) {
      String payloadType = (String) row.get("payload_type");
      byte[] payload = (byte[]) row.get("payload");
      EventApplier<S> applier = dispatchers.get(payloadType);
      if (applier == null) {
        throw new IllegalArgumentException("Unknown event type: " + payloadType);
      }
      applier.apply(state, payload);
    }
  }
}
