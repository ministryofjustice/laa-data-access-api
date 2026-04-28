package uk.gov.justice.laa.dstew.access.service.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.service.common.ServiceUtilities;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/** Update and existing application. */
@RequiredArgsConstructor
@Service
public class UpdateApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationValidations applicationValidations;
  private final DomainEventService domainEventService;
  private final ServiceUtilities serviceUtilities;
  private final ObjectMapper objectMapper;

  /**
   * Update an existing application.
   *
   * @param id application UUID
   * @param req DTO with update fields
   */
  @AllowApiCaseworker
  public void updateApplication(final UUID id, final ApplicationUpdateRequest req) {
    final ApplicationEntity entity = serviceUtilities.checkIfApplicationExists(id);
    applicationValidations.checkApplicationUpdateRequest(req);
    applicationMapper.updateApplicationEntity(entity, req);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);

    domainEventService.saveUpdateApplicationDomainEvent(entity, null);

    // Optional: create snapshot for audit/history
    objectMapper.convertValue(
        applicationMapper.toApplication(entity), new TypeReference<Map<String, Object>>() {});
  }
}
