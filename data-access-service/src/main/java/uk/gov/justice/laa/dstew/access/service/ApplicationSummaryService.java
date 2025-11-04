package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

/**
 * Service class for handling application summary requests.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationSummaryRepository applicationSummaryRepository;
  private final ApplicationMapper applicationMapper;

  public ApplicationSummaryService(
        final ApplicationSummaryRepository applicationSummaryRepository,
        final ApplicationMapper applicationMapper
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.applicationMapper = applicationMapper;
  }

  /**
   * Gets all application summaries.
   *
   * @return the list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<ApplicationSummary> getAllApplications(String applicationStatus, Integer page, Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    return applicationSummaryRepository.findByStatusCodeLookupEntity_Code(applicationStatus, pageDetails)
            .stream().map(applicationMapper::toApplicationSummary).toList();
  }

  /**
   * Gets all application summaries.
   *
   * @return the total of list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Integer getAllApplicationsTotal(String applicationStatus) {
    return applicationSummaryRepository.countByStatusCodeLookupEntity_Code(applicationStatus);
  }

}
