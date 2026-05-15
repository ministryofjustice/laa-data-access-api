package uk.gov.justice.laa.dstew.access.service.applications;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;

/** Service to get applications. */
@RequiredArgsConstructor
@Service
public class GetApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;

  /**
   * Retrieve a single application by ID.
   *
   * @param id application UUID
   * @return application DTO
   */
  @AllowApiCaseworker
  public ApplicationResponse getApplication(final UUID id) {
    final ApplicationEntity entity =
        ApplicationServiceHelper.getExistingApplication(id, applicationRepository);
    return applicationMapper.toApplication(entity);
  }
}
