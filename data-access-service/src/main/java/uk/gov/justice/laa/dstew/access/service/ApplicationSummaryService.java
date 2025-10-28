package uk.gov.justice.laa.dstew.access.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
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
  public List<ApplicationSummary> getAllApplications() {
    return applicationSummaryRepository.findAll().stream().map(applicationMapper::toApplicationSummary).toList();
  }
}
