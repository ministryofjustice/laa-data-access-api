package uk.gov.justice.laa.dstew.access.service;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.DraftApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateReq;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateReq;
import uk.gov.justice.laa.dstew.access.repository.DraftApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.DraftApplicationValidations;

/**
 * Service class for handling draft items requests.
 */
@Service
public class DraftApplicationService {

  private final DraftApplicationRepository draftApplicationRepository;
  private final DraftApplicationValidations applicationValidations;
  private final DraftApplicationMapper applicationMapper;

  /**
   * Create a service for applications for legal aid.
   *
   * @param draftApplicationMapper     JSON mapper to serialize the draft application.
   * @param draftApplicationRepository Manages reading and writing data to database.
   * @param applicationValidator       the validation methods for request DTO.
   */
  public DraftApplicationService(
          final DraftApplicationRepository draftApplicationRepository,
          final DraftApplicationMapper draftApplicationMapper,
          final DraftApplicationValidations applicationValidator) {
    this.draftApplicationRepository = draftApplicationRepository;
    this.applicationMapper = draftApplicationMapper;
    this.applicationValidations = applicationValidator;
  }

  /**
   * Creates an application.
   *
   * @param draftApplicationCreateReq the application to be created
   * @return the id of the created application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public UUID createApplication(DraftApplicationCreateReq draftApplicationCreateReq) {
    applicationValidations.checkCreateRequest(draftApplicationCreateReq);
    var applicationEntity = applicationMapper.toDraftApplicationEntity(draftApplicationCreateReq);
    applicationEntity.setAdditionalData(draftApplicationCreateReq.getAdditionalData());
    var savedEntity = draftApplicationRepository.save(applicationEntity);

    return savedEntity.getId();
  }

  /**
   * Gets an application for a given id.
   *
   * @param id the application id
   * @return the requested application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public DraftApplication getApplicationById(UUID id) {
    var applicationEntity = checkIfApplicationExists(id);
    return applicationMapper.toDraftApplication(applicationEntity);
  }

  protected DraftApplicationEntity checkIfApplicationExists(UUID id) {
    return draftApplicationRepository
            .findById(id)
            .orElseThrow(
                    () -> new ApplicationNotFoundException(String.format("No application found with id: %s", id)));
  }

  /**
   * Update an application for legal aid, keeping history.
   *
   * @param id the unique identifier of the application.
   * @param applicationUpdateReq the DTO containing the change.
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void updateApplication(UUID id, DraftApplicationUpdateReq applicationUpdateReq) {
    var applicationEntity = checkIfApplicationExists(id);

    applicationValidations.checkDraftApplicationUpdateRequest(applicationUpdateReq, applicationEntity);
    applicationEntity.setAdditionalData(applicationUpdateReq.getAdditionalData());
    applicationMapper.updateApplicationEntity(applicationEntity, applicationUpdateReq);

    draftApplicationRepository.save(applicationEntity);
  }
}
