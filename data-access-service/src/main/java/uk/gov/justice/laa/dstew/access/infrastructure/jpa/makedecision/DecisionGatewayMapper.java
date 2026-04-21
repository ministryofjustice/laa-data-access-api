package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/** Maps between DecisionDomain and DecisionEntity. */
public class DecisionGatewayMapper {

  /** Converts a DecisionEntity to a DecisionDomain. */
  public DecisionDomain toDomain(DecisionEntity entity) {
    if (entity == null) {
      return null;
    }
    Set<MeritsDecisionDomain> merits =
        entity.getMeritsDecisions() == null
            ? Set.of()
            : entity.getMeritsDecisions().stream()
                .map(this::toMeritsDomain)
                .collect(Collectors.toSet());
    return DecisionDomain.builder()
        .id(entity.getId())
        .overallDecision(OverallDecisionStatus.valueOf(entity.getOverallDecision().name()))
        .meritsDecisions(merits)
        .modifiedAt(entity.getModifiedAt())
        .build();
  }

  private MeritsDecisionDomain toMeritsDomain(MeritsDecisionEntity entity) {
    return MeritsDecisionDomain.builder()
        .id(entity.getId())
        .proceedingId(entity.getProceeding() != null ? entity.getProceeding().getId() : null)
        .decision(MeritsDecisionOutcome.valueOf(entity.getDecision().name()))
        .reason(entity.getReason())
        .justification(entity.getJustification())
        .modifiedAt(entity.getModifiedAt())
        .build();
  }

  /** Converts a DecisionDomain to a new DecisionEntity (INSERT path — id is null). */
  public DecisionEntity toNewEntity(DecisionDomain domain) {
    DecisionEntity entity = new DecisionEntity();
    entity.setOverallDecision(DecisionStatus.valueOf(domain.overallDecision().name()));
    entity.setModifiedAt(Instant.now());
    entity.setMeritsDecisions(Set.of());
    return entity;
  }

  public MeritsDecisionStatus toEntityDecisionStatus(MeritsDecisionOutcome outcome) {
    return MeritsDecisionStatus.valueOf(outcome.name());
  }
}
