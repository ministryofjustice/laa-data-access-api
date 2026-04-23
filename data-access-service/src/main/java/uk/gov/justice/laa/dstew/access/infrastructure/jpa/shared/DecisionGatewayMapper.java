package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntityV2;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;

/** Maps between DecisionDomain and DecisionEntityV2. Scalar fields only — no meritsDecisions. */
public class DecisionGatewayMapper {

  /** Converts a DecisionEntityV2 to a DecisionDomain. */
  public DecisionDomain toDomain(DecisionEntityV2 entity) {
    if (entity == null) {
      return null;
    }
    return DecisionDomain.builder()
        .id(entity.getId())
        .overallDecision(OverallDecisionStatus.valueOf(entity.getOverallDecision().name()))
        .modifiedAt(entity.getModifiedAt())
        .build();
  }

  /** Creates a new DecisionEntityV2 from a domain (INSERT path — id is null). */
  public DecisionEntityV2 toNewEntity(DecisionDomain domain) {
    DecisionEntityV2 entity = new DecisionEntityV2();
    entity.setOverallDecision(DecisionStatus.valueOf(domain.overallDecision().name()));
    entity.setModifiedAt(Instant.now());
    return entity;
  }

  /** Applies domain values to an existing managed entity (UPDATE path). */
  public void applyToEntity(DecisionDomain domain, DecisionEntityV2 entity) {
    entity.setOverallDecision(DecisionStatus.valueOf(domain.overallDecision().name()));
    entity.setModifiedAt(Instant.now());
  }
}
