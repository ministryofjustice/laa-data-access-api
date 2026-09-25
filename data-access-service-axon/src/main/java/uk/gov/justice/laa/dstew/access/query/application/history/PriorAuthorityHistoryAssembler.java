package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataId;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;

/**
 * Validates, sorts, groups, and hydrates prior-authority history rows into immutable query records.
 *
 * <p>Rows are sorted by {@code occurredAt} then {@code eventId} before insertion-ordered grouping
 * so each group's position reflects its earliest event. Each group is validated to ensure every row
 * has the same {@code priorAuthorityType}.
 */
@Component
@RequiredArgsConstructor
public class PriorAuthorityHistoryAssembler {

  private final PriorAuthorityDataRepository priorAuthorityDataRepository;

  /**
   * Assembles flat PA history rows into validated, immutable groups ordered by earliest event.
   *
   * @param rows all rows for an application, in any order
   * @return immutable list of groups ordered by the earliest event in the group
   * @throws ApplicationHistoryIntegrityException if a group has conflicting prior-authority types
   */
  public List<PriorAuthorityHistoryGroupResult> assemble(
      List<PriorAuthorityHistoryReadModel> rows) {
    var rowsByPriorAuthorityId = new LinkedHashMap<UUID, List<PriorAuthorityHistoryReadModel>>();
    rows.stream()
        .sorted(
            Comparator.comparing(PriorAuthorityHistoryReadModel::getOccurredAt)
                .thenComparing(PriorAuthorityHistoryReadModel::getEventId))
        .forEach(
            row ->
                rowsByPriorAuthorityId
                    .computeIfAbsent(row.getPriorAuthorityId(), ignored -> new ArrayList<>())
                    .add(row));

    return rowsByPriorAuthorityId.entrySet().stream()
        .map(entry -> toGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private PriorAuthorityHistoryGroupResult toGroup(
      UUID priorAuthorityId, List<PriorAuthorityHistoryReadModel> rows) {
    UUID applicationId = rows.get(0).getApplicationId();

    Set<String> distinctTypes =
        rows.stream()
            .map(PriorAuthorityHistoryReadModel::getPriorAuthorityType)
            .collect(Collectors.toSet());
    if (distinctTypes.size() > 1) {
      throw new ApplicationHistoryIntegrityException(
          applicationId,
          priorAuthorityId,
          "conflicting priorAuthorityType values: " + distinctTypes);
    }

    String priorAuthorityType = distinctTypes.iterator().next();
    List<PriorAuthorityHistoryEventResult> events = rows.stream().map(this::toEvent).toList();
    return new PriorAuthorityHistoryGroupResult(priorAuthorityId, priorAuthorityType, events);
  }

  private PriorAuthorityHistoryEventResult toEvent(PriorAuthorityHistoryReadModel row) {
    String eventDescription = decisionDescription(row);
    return new PriorAuthorityHistoryEventResult(
        row.getEventType(),
        row.getOccurredAt(),
        row.getServiceName(),
        eventDescription,
        row.getCaseworkerId());
  }

  private String decisionDescription(PriorAuthorityHistoryReadModel row) {
    if (!"PRIOR_AUTHORITY_MAKE_DECISION_GRANTED".equals(row.getEventType())
        && !"PRIOR_AUTHORITY_MAKE_DECISION_REFUSED".equals(row.getEventType())) {
      return null;
    }

    PriorAuthorityDataId dataId =
        new PriorAuthorityDataId(row.getPriorAuthorityId(), row.getItemVersion());
    PriorAuthorityData data = priorAuthorityDataRepository.findById(dataId).orElse(null);
    String inferredDecision = inferDecisionFromEventType(row.getEventType());
    String decision =
        data == null
                || data.getPayload().decision() == null
                || data.getPayload().decision().isBlank()
            ? inferredDecision
            : data.getPayload().decision();
    String decisionJustification = data == null ? null : data.getPayload().decisionJustification();

    String outcome =
        DecisionValue.GRANTED.name().equals(decision)
            ? "Granted"
            : DecisionValue.REFUSED.name().equals(decision) ? "Refused" : decision;

    String decisionAt = Objects.toString(row.getOccurredAt(), null);
    if (decisionJustification == null || decisionJustification.isBlank()) {
      return "Outcome: " + outcome + ", Decision at: " + decisionAt;
    }
    return "Outcome: "
        + outcome
        + ", Decision at: "
        + decisionAt
        + ", Justification: "
        + decisionJustification;
  }

  private String inferDecisionFromEventType(String eventType) {
    return "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED".equals(eventType)
        ? DecisionValue.GRANTED.name()
        : DecisionValue.REFUSED.name();
  }
}
