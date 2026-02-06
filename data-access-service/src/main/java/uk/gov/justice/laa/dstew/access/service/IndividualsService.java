package uk.gov.justice.laa.dstew.access.service;

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
   * Requires the 'ApplicationReader' role.
   *
   * @param page the page number (zero-based), defaults to 0 if null
   * @param pageSize the number of items per page, defaults to 10 if null
   * @return a {@link Page} of {@link Individual} objects
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Page<Individual> getIndividuals(Integer page, Integer pageSize) {
    int pageNumber = page != null ? page : 0;
    int size = pageSize != null ? pageSize : 10;

    Pageable pageable = PageRequest.of(pageNumber, size);
    Page<IndividualEntity> resultPage = individualRepository.findAll(pageable);

    return resultPage.map(individualMapper::toIndividual);
  }
}
