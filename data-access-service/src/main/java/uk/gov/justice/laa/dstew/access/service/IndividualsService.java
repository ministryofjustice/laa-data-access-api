package uk.gov.justice.laa.dstew.access.service;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.createPageable;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.wrapResult;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.IndividualMapper;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;

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
   * @return a {@link PaginatedResult} containing the page and validated pagination parameters
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public PaginatedResult<Individual> getIndividuals(Integer page, Integer pageSize) {
    Pageable pageable = createPageable(page, pageSize);
    Page<IndividualEntity> resultPage = individualRepository.findAll(pageable);
    return wrapResult(page, pageSize, resultPage.map(individualMapper::toIndividual));
  }
}