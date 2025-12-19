package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.Status;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSummarySpecification;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service class responsible for retrieving and managing {@link ApplicationSummary} data.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationSummaryRepository applicationSummaryRepository;
  private final CaseworkerRepository caseworkerRepository;
  private final ApplicationSummaryMapper mapper;

  /**
   * Constructs a new {@link ApplicationSummaryService} with the required repository and mapper.
   *
   * @param applicationSummaryRepository the repository used to access application summary data
   * @param applicationSummaryMapper the mapper used to convert entities into API-facing models
   */
  public ApplicationSummaryService(
      final ApplicationSummaryRepository applicationSummaryRepository,
      final ApplicationSummaryMapper applicationSummaryMapper,
      final CaseworkerRepository caseworkerRepository
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.mapper = applicationSummaryMapper;
    this.caseworkerRepository = caseworkerRepository;
  }

  /**
   * Retrieves a paginated list of {@link ApplicationSummary} objects filtered by application status.
   *
   * @param applicationStatus the {@link Status} used to filter results on application status
   * @param laaReference used to filter results on application reference
   * @param clientFirstName used to filter results on linked individuals first name
   * @param clientLastName used to filter results on  linked individuals last name
   * @param page the page number to retrieve (zero-based index)
   * @param pageSize the maximum number of results to return per page
   * @return a list of {@link ApplicationSummary} instances matching the filter criteria
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Page<ApplicationSummary> getAllApplications(
          Status applicationStatus,
          String laaReference,
          String clientFirstName,
          String clientLastName,
          UUID userId,
          Integer page,
          Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    if (userId != null && caseworkerRepository.countById(userId) == 0L) {
      throw new ValidationException(List.of("Caseworker not found"));
    }

    return applicationSummaryRepository
            .findAll(ApplicationSummarySpecification
                            .filterBy(applicationStatus,
                                    laaReference,
                                    clientFirstName,
                                    clientLastName,
                                    userId),
                    pageDetails)
            .map(mapper::toApplicationSummary);
  }
}
