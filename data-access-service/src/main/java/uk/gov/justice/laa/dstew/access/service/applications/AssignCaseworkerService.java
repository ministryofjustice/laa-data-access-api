package uk.gov.justice.laa.dstew.access.service.applications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainEvents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/** Service for assigning caseworkers to an application. */
@RequiredArgsConstructor
@Service
public class AssignCaseworkerService {

  private final CaseworkerRepository caseworkerRepository;
  private final ApplicationRepository applicationRepository;
  private final SaveDomainEventService saveDomainEventService;
  final ApplicationValidations applicationValidations;

  /**
   * Assigns a caseworker to an application.
   *
   * @param caseworkerId the UUID of the caseworker to assign
   * @param applicationIds the UUIDs of the applications to assign the caseworker to
   * @throws ResourceNotFoundException if the application or caseworker does not exist
   */
  @Transactional
  @AllowApiCaseworker
  public void assignCaseworker(
      @NonNull final UUID caseworkerId,
      final List<UUID> applicationIds,
      final EventHistoryRequest eventHistoryRequest) {
    final CaseworkerEntity caseworker = checkIfCaseworkerExists(caseworkerId);

    final List<ApplicationEntity> applications = checkIfAllApplicationsExist(applicationIds);

    applications.forEach(
        app -> {
          if (!applicationCurrentCaseworkerIsCaseworker(app, caseworker)) {
            app.setCaseworker(caseworker);
            app.setModifiedAt(Instant.now());
            applicationRepository.save(app);
          }

          saveDomainEventService.saveAssignApplicationDomainEvent(
              app.getId(), caseworker.getId(), eventHistoryRequest.getEventDescription());
        });
  }

  /**
   * Checks that applications exist for all the IDs provided.
   *
   * @param ids Collection of UUIDs of applications
   * @return found entity
   */
  private List<ApplicationEntity> checkIfAllApplicationsExist(final List<UUID> ids) {
    applicationValidations.checkApplicationIdList(ids);
    var idsToFetch = ids.stream().distinct().toList();
    var applications = applicationRepository.findAllById(idsToFetch);
    List<UUID> fetchedApplicationsIds =
        applications.stream().map(ApplicationEntity::getId).toList();
    String missingIds =
        idsToFetch.stream()
            .filter(appId -> !fetchedApplicationsIds.contains(appId))
            .map(UUID::toString)
            .collect(Collectors.joining(","));
    if (!missingIds.isEmpty()) {
      String exceptionMsg = "No application found with ids: " + missingIds;
      throw new ResourceNotFoundException(exceptionMsg);
    }
    return applications;
  }

  /**
   * Check if an application has a caseworker assigned already and checks if the assigned caseworker
   * matches the given caseworker.
   */
  private static boolean applicationCurrentCaseworkerIsCaseworker(
      ApplicationEntity application, CaseworkerEntity caseworker) {
    return application.getCaseworker() != null && application.getCaseworker().equals(caseworker);
  }

  /**
   * Check existence of a caseworker by ID.
   *
   * @param caseworkerId userid of caseworker
   * @return found entity
   */
  private CaseworkerEntity checkIfCaseworkerExists(final UUID caseworkerId) {
    return caseworkerRepository
        .findById(caseworkerId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No caseworker found with id: %s", caseworkerId)));
  }
}
