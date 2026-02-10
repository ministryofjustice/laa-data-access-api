package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.IndividualMapper;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.Paging;
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
   * @param page the page number (zero-based), defaults to 0 if null
   * @param pageSize the number of items per page, defaults to 10 if null
   * @return a {@link Page} of {@link Individual} objects
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Page<Individual> getIndividuals(Integer page, Integer pageSize) {
    int pageNumber = (page == null || page < 0) ? 0 : page;
    int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

    Pageable pageable = PageRequest.of(pageNumber, size);
    Page<IndividualEntity> resultPage = individualRepository.findAll(pageable);

    return resultPage.map(individualMapper::toIndividual);
  }

  /**
   * Handles all logic for building IndividualsResponse, including paging and defaults.
   *
   * @param page the page number (1-based), may be null for default
   * @param pageSize the number of items per page, may be null for default
   * @return IndividualsResponse with paging info and individuals
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public IndividualsResponse getIndividualsResponse(Integer page, Integer pageSize) {
    int resolvedPage = (page == null || page < 1) ? 1 : page;
    int resolvedPageSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
    Page<Individual> individualsReturned = getIndividuals(resolvedPage - 1, resolvedPageSize);
    List<Individual> individuals = individualsReturned.stream().toList();
    Paging paging = new Paging();
    paging.setPage(resolvedPage);
    paging.pageSize(resolvedPageSize);
    paging.totalRecords((int) individualsReturned.getTotalElements());
    paging.itemsReturned(individuals.size());
    IndividualsResponse response = new IndividualsResponse();
    response.setIndividuals(individuals);
    response.setPaging(paging);
    return response;
  }
}
