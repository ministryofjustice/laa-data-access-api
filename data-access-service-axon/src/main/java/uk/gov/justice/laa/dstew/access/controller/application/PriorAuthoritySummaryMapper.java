package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.List;
import uk.gov.justice.laa.dstew.access.model.PriorAuthoritySummary;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

final class PriorAuthoritySummaryMapper {

  private PriorAuthoritySummaryMapper() {}

  static List<PriorAuthoritySummary> toSummaries(List<PriorAuthorityReadModel> priorAuthorities) {
    return priorAuthorities.stream()
        .map(
            priorAuthority ->
                new PriorAuthoritySummary()
                    .priorAuthorityId(priorAuthority.getPriorAuthorityId())
                    .status(PriorAuthoritySummary.StatusEnum.fromValue(priorAuthority.getStatus()))
                    .priorAuthorityType(
                        priorAuthority.getPriorAuthorityType() == null
                            ? null
                            : PriorAuthoritySummary.PriorAuthorityTypeEnum.fromValue(
                                priorAuthority.getPriorAuthorityType()))
                    .decision(
                        priorAuthority.getDecision() == null
                            ? null
                            : PriorAuthoritySummary.DecisionEnum.fromValue(
                                priorAuthority.getDecision()))
                    .createdAt(priorAuthority.getCreatedAt().atOffset(ZoneOffset.UTC)))
        .toList();
  }
}
