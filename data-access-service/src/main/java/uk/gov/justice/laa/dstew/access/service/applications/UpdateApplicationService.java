package uk.gov.justice.laa.dstew.access.service.applications;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainEvents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/** Update and existing application. */
@RequiredArgsConstructor
@Service
public class UpdateApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationValidations applicationValidations;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Update an existing application.
   *
   * @param id application UUID
   * @param req DTO with update fields
   */
  @Transactional
  @AllowApiCaseworker
  public void updateApplication(final UUID id, final ApplicationUpdateRequest req) {
    final ApplicationEntity entity =
        ApplicationServiceHelper.getExistingApplication(id, applicationRepository);
    applicationValidations.checkApplicationUpdateRequest(req);
    applicationMapper.updateApplicationEntity(entity, req);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);

    saveDomainEventService.saveUpdateApplicationDomainEvent(entity, null);
  }
}
