package uk.gov.justice.laa.dstew.access.service;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePage;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePageSize;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.IndividualMapper;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;

/**
 * Service for managing individual records.
 * Provides business logic for retrieving individuals with pagination support.
 */
@Service
public class IndividualsService {

  private final IndividualRepository individualRepository;
  private final IndividualMapper individualMapper;

  public IndividualsService(final IndividualRepository individualRepository,
                            final IndividualMapper individualMapper) {
    this.individualRepository = individualRepository;
    this.individualMapper = individualMapper;
  }

  /**
   * Retrieves a paginated list of individuals.
   *
   * @param page the page number (one-based)
   * @param pageSize the number of items per page
   * @return a {@link Page} of {@link Individual} objects
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Page<Individual> getIndividuals(Integer page, Integer pageSize) {
    // Validate pagination parameters
    int validatedPage = validatePage(page);
    int validatedPageSize = validatePageSize(pageSize);

    // Convert to zero-based index for Spring Data
    Pageable pageable = PageRequest.of(validatedPage - 1, validatedPageSize);
    Page<IndividualEntity> resultPage = individualRepository.findAll(pageable);

    return resultPage.map(individualMapper::toIndividual);
  }
}