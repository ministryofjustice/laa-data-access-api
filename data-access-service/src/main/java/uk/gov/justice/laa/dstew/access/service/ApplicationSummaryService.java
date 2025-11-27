package uk.gov.justice.laa.dstew.access.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryPage;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSummarySpecification;

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
   * @param applicationSummaryMapper the mapper used to convert entities into API-facing models
   */
  public ApplicationSummaryService(
      final ApplicationSummaryRepository applicationSummaryRepository,
      final ApplicationSummaryMapper applicationSummaryMapper
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.mapper = applicationSummaryMapper;
  }

  /**
   * Retrieves a paginated list of {@link ApplicationSummary} objects filtered by application status.
   *
   * @param applicationStatus the {@link ApplicationStatus} used to filter results on application status
   * @param applicationReference used to filter results on application reference
   * @param firstName used to filter results on linked individuals first name
   * @param lastName used to filter results on  linked individuals last name
   * @param page the page number to retrieve (zero-based index)
   * @param pageSize the maximum number of results to return per page
   * @return a list of {@link ApplicationSummary} instances matching the filter criteria
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public ApplicationSummaryPage getAllApplications(
          ApplicationStatus applicationStatus,
          String applicationReference,
          String firstName,
          String lastName,
          Integer page,
          Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    Page<ApplicationSummaryEntity> entities = applicationSummaryRepository
            .findAll(ApplicationSummarySpecification
                            .filterBy(applicationStatus,
                                    applicationReference,
                                    firstName,
                                    lastName),
                                    pageDetails);

    ApplicationSummaryPage applicationsReturned = new ApplicationSummaryPage();
    applicationsReturned.setApplications(entities
            .getContent()
            .stream()
            .map(mapper::toApplicationSummary)
            .toList());
    applicationsReturned.setTotalItems(entities.getTotalElements());

    return applicationsReturned;
  }
}
