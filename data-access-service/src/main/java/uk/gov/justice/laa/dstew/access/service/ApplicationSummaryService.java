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
 * Service class for handling application summary requests.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationSummaryRepository applicationSummaryRepository;
  private final ApplicationSummaryMapper mapper;

  public ApplicationSummaryService(
      final ApplicationSummaryRepository applicationSummaryRepository,
      final ApplicationSummaryMapper applicationMapper
  ) {
    this.applicationSummaryRepository = applicationSummaryRepository;
    this.mapper = applicationMapper;
  }

  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<ApplicationSummary> getAllApplications(ApplicationStatus applicationStatus, Integer page, Integer pageSize) {
    Pageable pageDetails = PageRequest.of(page, pageSize);

    return applicationSummaryRepository.findAll((root, query, cb) ->
            cb.equal(root.get("status"), applicationStatus), pageDetails)
        .stream()
        .map(mapper::toApplicationSummary)
        .toList();
  }

  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Integer getAllApplicationsTotal(ApplicationStatus applicationStatus) {
    return (int) applicationSummaryRepository.count((root, query, cb) ->
        cb.equal(root.get("status"), applicationStatus));
  }
}
