package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntityV2;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/** Maps between MeritsDecisionDomain and MeritsDecisionEntityV2. */
public class MeritsDecisionGatewayMapper {

  /** Converts a MeritsDecisionEntityV2 to a MeritsDecisionDomain. */
  public MeritsDecisionDomain toDomain(MeritsDecisionEntityV2 entity) {
    if (entity == null) {
      return null;
    }
    return MeritsDecisionDomain.builder()
        .id(entity.getId())
        .decision(MeritsDecisionOutcome.valueOf(entity.getDecision().name()))
        .reason(entity.getReason())
        .justification(entity.getJustification())
        .modifiedAt(entity.getModifiedAt())
        .build();
  }

  /** Creates a new MeritsDecisionEntityV2 from a domain (INSERT path — id is null). */
  public MeritsDecisionEntityV2 toNewEntity(MeritsDecisionDomain domain) {
    MeritsDecisionEntityV2 entity = new MeritsDecisionEntityV2();
    entity.setDecision(MeritsDecisionStatus.valueOf(domain.decision().name()));
    entity.setReason(domain.reason());
    entity.setJustification(domain.justification());
    entity.setModifiedAt(Instant.now());
    return entity;
  }

  /** Applies domain values to an existing managed entity (UPDATE path). */
  public void applyToEntity(MeritsDecisionDomain domain, MeritsDecisionEntityV2 entity) {
    entity.setDecision(MeritsDecisionStatus.valueOf(domain.decision().name()));
    entity.setReason(domain.reason());
    entity.setJustification(domain.justification());
    entity.setModifiedAt(Instant.now());
  }
}
