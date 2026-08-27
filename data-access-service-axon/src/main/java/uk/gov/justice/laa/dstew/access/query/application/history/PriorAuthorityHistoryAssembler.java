package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Validates, sorts, groups, and hydrates prior-authority history rows into immutable query records.
 *
 * <p>Rows are sorted by {@code occurredAt} then {@code eventId} before insertion-ordered grouping
 * so each group's position reflects its earliest event. Each group is validated to ensure every row
 * has the same {@code priorAuthorityType}.
 */
@Component
public class PriorAuthorityHistoryAssembler {

  /**
   * Assembles flat PA history rows into validated, immutable groups ordered by earliest event.
   *
   * @param rows all rows for an application, in any order
   * @return immutable list of groups ordered by the earliest event in the group
   * @throws ApplicationHistoryIntegrityException if a group has conflicting prior-authority types
   */
  public List<PriorAuthorityHistoryGroupResult> assemble(
      List<PriorAuthorityHistoryReadModel> rows) {
    var rowsBySubmissionId = new LinkedHashMap<UUID, List<PriorAuthorityHistoryReadModel>>();
    rows.stream()
        .sorted(
            Comparator.comparing(PriorAuthorityHistoryReadModel::getOccurredAt)
                .thenComparing(PriorAuthorityHistoryReadModel::getEventId))
        .forEach(
            row ->
                rowsBySubmissionId
                    .computeIfAbsent(row.getSubmissionId(), ignored -> new ArrayList<>())
                    .add(row));

    return rowsBySubmissionId.entrySet().stream()
        .map(entry -> toGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private PriorAuthorityHistoryGroupResult toGroup(
      UUID submissionId, List<PriorAuthorityHistoryReadModel> rows) {
    UUID applicationId = rows.getFirst().getApplicationId();

    Set<String> distinctTypes =
        rows.stream()
            .map(PriorAuthorityHistoryReadModel::getPriorAuthorityType)
            .collect(Collectors.toSet());
    if (distinctTypes.size() > 1) {
      throw new ApplicationHistoryIntegrityException(
          applicationId, submissionId, "conflicting priorAuthorityType values: " + distinctTypes);
    }

    String priorAuthorityType = distinctTypes.iterator().next();
    List<PriorAuthorityHistoryEventResult> events = rows.stream().map(this::toEvent).toList();
    return new PriorAuthorityHistoryGroupResult(submissionId, priorAuthorityType, events);
  }

  private PriorAuthorityHistoryEventResult toEvent(PriorAuthorityHistoryReadModel row) {
    return new PriorAuthorityHistoryEventResult(
        row.getEventType(), row.getOccurredAt(), row.getServiceName(), null);
  }
}
