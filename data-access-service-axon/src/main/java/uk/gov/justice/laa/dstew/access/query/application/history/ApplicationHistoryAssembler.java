package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupJustificationStore;

/** Hydrates flat application-history rows into immutable query records. */
@Component
@RequiredArgsConstructor
public class ApplicationHistoryAssembler {

  private static final Set<String> DECISION_EVENT_TYPES =
      Set.of("APPLICATION_MAKE_DECISION_GRANTED", "APPLICATION_MAKE_DECISION_REFUSED");

  private final ApplicationDataStore applicationDataStore;
  private final LinkedGroupJustificationStore justificationStore;

  /**
   * Maps history rows to query records, lazily hydrating {@code eventDescription} from the
   * versioned application-data store for event types that carry a human-readable description.
   *
   * @param rows all rows for an application, in their requested order
   * @return immutable list of hydrated event results, same order as {@code rows}
   */
  public List<ApplicationHistoryEventResult> assemble(List<ApplicationHistoryReadModel> rows) {
    var ids =
        rows.stream()
            .map(ApplicationHistoryReadModel::getJustificationId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    Map<UUID, String> justifications = ids.isEmpty() ? Map.of() : justificationStore.findAll(ids);
    return rows.stream().map(row -> toEvent(row, justifications)).toList();
  }

  private ApplicationHistoryEventResult toEvent(
      ApplicationHistoryReadModel row, Map<UUID, String> justifications) {
    return new ApplicationHistoryEventResult(
        row.getApplicationId(),
        row.getEventType(),
        row.getOccurredAt(),
        row.getServiceName(),
        eventDescription(row, justifications),
        row.getCaseworkerId());
  }

  private String eventDescription(
      ApplicationHistoryReadModel row, Map<UUID, String> justifications) {
    if (row.getJustificationId() != null) {
      String text = justifications.get(row.getJustificationId());
      if (text == null) {
        throw new ApplicationHistoryIntegrityException(
            row.getApplicationId(),
            null,
            "Missing linked-group justification " + row.getJustificationId());
      }
      return text;
    }
    boolean decision = DECISION_EVENT_TYPES.contains(row.getEventType());
    boolean note = "APPLICATION_NOTE_CREATED".equals(row.getEventType());
    if ((!decision && !note) || row.getDataVersion() == null) {
      return null;
    }
    ApplicationDataPayload data =
        applicationDataStore.findPayload(row.getApplicationId(), row.getDataVersion()).orElse(null);
    if (data == null) {
      return null;
    }
    if (note) {
      return data.notes().isEmpty() ? null : data.notes().getLast().noteText();
    }
    return data.decisionEventDescription();
  }
}
