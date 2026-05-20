package uk.gov.justice.laa.dstew.access.service.individuals;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.createPageable;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.wrapResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.IndividualMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.specification.IndividualSpecification;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;

/**
 * Service for managing individual records. Provides business logic for retrieving individuals with
 * pagination support.
 */
@Service
public class GetAllIndividualsService {

  private final IndividualRepository individualRepository;
  private final IndividualMapper individualMapper;
  private final ApplicationRepository applicationRepository;
  private final ObjectMapper objectMapper;

  /** Constructor for managing individual records. */
  public GetAllIndividualsService(
      final IndividualRepository individualRepository,
      final IndividualMapper individualMapper,
      ApplicationRepository applicationRepository,
      final ObjectMapper objectMapper) {
    this.individualRepository = individualRepository;
    this.individualMapper = individualMapper;
    this.applicationRepository = applicationRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieves a paginated and filtered list of individuals based on applicationId and
   * individualType.
   *
   * @param page the page number (one-based)
   * @param pageSize the number of items per page
   * @param applicationId the application UUID to filter by (nullable)
   * @param individualType the individual type to filter by (nullable)
   * @return a {@link PaginatedResult} containing the page and validated pagination parameters
   */
  @AllowApiCaseworker
  public PaginatedResult<IndividualResponse> getIndividuals(
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType individualType,
      IncludedAdditionalData include) {
    Specification<IndividualEntity> specification =
        buildSpecification(applicationId, individualType);
    Pageable pageable = createPageable(page, pageSize);
    Page<IndividualEntity> resultPage = individualRepository.findAll(specification, pageable);
    if (individualType == IndividualType.CLIENT && include != null) {
      ApplicationEntity application = applicationRepository.findById(applicationId).orElseThrow();

      return wrapResult(
          page,
          pageSize,
          resultPage.map(
              individual ->
                  individualMapper.toExtendedIndividual(
                      individual,
                      individualType,
                      include,
                      objectMapper.convertValue(
                          application.getApplicationContent(), ApplicationContent.class))));
    }
    return wrapResult(page, pageSize, resultPage.map(individualMapper::toIndividual));
  }

  /** Builds a Specification for filtering individuals by applicationId and individualType. */
  private Specification<IndividualEntity> buildSpecification(
      UUID applicationId, IndividualType individualType) {
    return IndividualSpecification.filterApplicationId(applicationId)
        .and(IndividualSpecification.filterIndividualType(individualType));
  }
}
