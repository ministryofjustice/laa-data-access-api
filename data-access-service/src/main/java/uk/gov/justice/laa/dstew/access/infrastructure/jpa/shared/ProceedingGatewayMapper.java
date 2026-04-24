package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntityV2;

/** Maps between ProceedingDomain and ProceedingEntityV2. */
public class ProceedingGatewayMapper {

  private final MeritsDecisionGatewayMapper meritsMapper = new MeritsDecisionGatewayMapper();

  /** Converts a ProceedingEntityV2 to a ProceedingDomain. */
  public ProceedingDomain toDomain(ProceedingEntityV2 entity) {
    if (entity == null) {
      return null;
    }
    return ProceedingDomain.builder()
        .id(entity.getId())
        .applyProceedingId(entity.getApplyProceedingId())
        .description(entity.getDescription())
        .isLead(entity.isLead())
        .proceedingContent(entity.getProceedingContent())
        .meritsDecision(
            entity.getMeritsDecision() != null
                ? meritsMapper.toDomain(entity.getMeritsDecision())
                : null)
        .build();
  }

  /** Creates a new ProceedingEntityV2 from a domain (INSERT path — id is null). */
  public ProceedingEntityV2 toNewEntity(ProceedingDomain domain) {
    ProceedingEntityV2 entity = new ProceedingEntityV2();
    entity.setLead(domain.isLead());
    entity.setProceedingContent(domain.proceedingContent());
    // applyProceedingId and description are pre-extracted from the content map in
    // CreateApplicationUseCase so we never need to parse the JSON string here.
    entity.setApplyProceedingId(domain.applyProceedingId());
    entity.setDescription(domain.description() != null ? domain.description() : "");
    entity.setCreatedBy("");
    entity.setUpdatedBy("");
    // meritsDecision is null on create; set via applyToEntity on the make-decision path
    return entity;
  }

  /** Applies domain values to an existing managed entity (UPDATE path). */
  public void applyToEntity(ProceedingDomain domain, ProceedingEntityV2 entity) {
    entity.setLead(domain.isLead());
    entity.setProceedingContent(domain.proceedingContent());
    if (domain.meritsDecision() != null) {
      if (entity.getMeritsDecision() != null) {
        meritsMapper.applyToEntity(domain.meritsDecision(), entity.getMeritsDecision());
      } else {
        entity.setMeritsDecision(meritsMapper.toNewEntity(domain.meritsDecision()));
      }
    }
  }
}
