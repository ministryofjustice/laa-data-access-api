package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

/**
 * Service class responsible for retrieving and managing {@link ApplicationSummary} data.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationSummaryRepository applicationSummaryRepository;
  private final ApplicationSummaryMapper mapper;

  /**
   * Constructs a new {@link ApplicationSummaryService} with the required repository and mapper.
   *
   * @param applicationSummaryRepository the repository used to access application summary data
   * @param applicationMapper the mapper used to convert entities into API-facing models
   */
  public ApplicationSummaryService(
      final ApplicationSummaryRepository applicationSummaryRepository,
      final ApplicationSummaryMapper applicationMapper
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.mapper = applicationMapper;
  }

  /**
   * Retrieves a paginated list of {@link ApplicationSummary} objects filtered by application status.
   *
   * @param applicationStatus the {@link ApplicationStatus} used to filter results
   * @param page the page number to retrieve (zero-based index)
   * @param pageSize the maximum number of results to return per page
   * @return a list of {@link ApplicationSummary} instances matching the filter criteria
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<ApplicationSummary> getAllApplications(ApplicationStatus applicationStatus, Integer page, Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    return applicationSummaryRepository.findAll((root, query, cb) ->
            cb.equal(root.get("status"), applicationStatus), pageDetails)
        .stream()
        .map(mapper::toApplicationSummary)
        .toList();
  }


  /**
   * Retrieves the total count of applications with a specific {@link ApplicationStatus}.
   *
   * @param applicationStatus the {@link ApplicationStatus} used to filter applications
   * @return the total number of applications matching the provided status
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Integer getAllApplicationsTotal(ApplicationStatus applicationStatus) {
    return (int) applicationSummaryRepository.count((root, query, cb) ->
        cb.equal(root.get("status"), applicationStatus));
  }
}
