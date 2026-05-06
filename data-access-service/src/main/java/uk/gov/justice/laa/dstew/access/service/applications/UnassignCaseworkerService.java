package uk.gov.justice.laa.dstew.access.service.applications;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainEvents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;

/** Service for unassigning a caseworker from an application. */
@RequiredArgsConstructor
@Service
public class UnassignCaseworkerService {
  private final ApplicationRepository applicationRepository;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Unassigns a caseworker from an application.
   *
   * @param applicationId the UUID of the application to update
   * @throws ResourceNotFoundException if the application does not exist
   */
  @Transactional
  @AllowApiCaseworker
  public void unassignCaseworker(final UUID applicationId, EventHistoryRequest history) {
    final ApplicationEntity entity =
        ApplicationServiceHelper.getExistingApplication(applicationId, applicationRepository);

    if (entity.getCaseworker() == null) {
      return;
    }

    entity.setCaseworker(null);
    entity.setModifiedAt(Instant.now());

    applicationRepository.save(entity);

    saveDomainEventService.saveUnassignApplicationDomainEvent(
        entity.getId(), null, history.getEventDescription());
  }
}
