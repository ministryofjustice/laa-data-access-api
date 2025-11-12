package uk.gov.justice.laa.dstew.access.service;

import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSummarySpecification;

/**
 * Service class for handling application summary requests.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationSummaryRepository applicationSummaryRepository;
  private final ApplicationSummaryMapper mapper;

  public ApplicationSummaryService(
        final ApplicationSummaryRepository applicationSummaryRepository,
        final ApplicationSummaryMapper applicationSummaryMapper
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.mapper = applicationSummaryMapper;
  }

  /**
   * Gets all application summaries.
   *
   * @return the list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<ApplicationSummary> getAllApplications(ApplicationStatus applicationStatus, Integer page, Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    Page<ApplicationSummaryEntity> applicationSummaryPage = applicationSummaryRepository
            .findAll(ApplicationSummarySpecification.isStatus(applicationStatus), pageDetails);

    List<ApplicationSummaryEntity> allApplications = applicationSummaryPage.getContent();

    return allApplications.stream().map(mapper::toApplicationSummary).toList();
  }

  /**
   * Gets all application summaries.
   *
   * @return the total of list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Integer getAllApplicationsTotal(ApplicationStatus applicationStatus) {
    return applicationSummaryRepository
            .findAll(ApplicationSummarySpecification.isStatus(applicationStatus))
            .size();
  }

}
