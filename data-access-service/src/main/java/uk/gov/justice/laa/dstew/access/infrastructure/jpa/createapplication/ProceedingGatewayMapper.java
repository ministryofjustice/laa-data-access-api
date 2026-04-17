package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication;

import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;

/** Maps between ProceedingDomain and ProceedingEntity. */
public class ProceedingGatewayMapper {

  /** Converts a ProceedingDomain to a ProceedingEntity. */
  public ProceedingEntity toEntity(ProceedingDomain domain, UUID applicationId) {
    if (domain == null) {
      return null;
    }
    Map<String, Object> content = domain.proceedingContent();
    ProceedingEntity entity = new ProceedingEntity();
    entity.setApplicationId(applicationId);
    entity.setLead(domain.isLead());
    entity.setProceedingContent(content);
    Object idObj = content.get("id");
    if (idObj != null) {
      entity.setApplyProceedingId(UUID.fromString(idObj.toString()));
    }
    Object desc = content.get("description");
    entity.setDescription(desc != null ? desc.toString() : "");
    entity.setCreatedBy("");
    entity.setUpdatedBy("");
    return entity;
  }

  /** Converts a ProceedingEntity to a ProceedingDomain. */
  public ProceedingDomain toDomain(ProceedingEntity entity) {
    if (entity == null) {
      return null;
    }
    return new ProceedingDomain(
        entity.getApplicationId(), entity.isLead(), entity.getProceedingContent());
  }
}
