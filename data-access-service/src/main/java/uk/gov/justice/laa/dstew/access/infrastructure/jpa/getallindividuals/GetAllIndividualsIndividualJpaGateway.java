package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.createPageable;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.specification.IndividualSpecification;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsIndividualGateway;

/** JPA implementation of {@link GetAllIndividualsIndividualGateway}. */
public class GetAllIndividualsIndividualJpaGateway implements GetAllIndividualsIndividualGateway {

  private final IndividualRepository individualRepository;
  private final GetAllIndividualsGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param individualRepository the JPA repository
   * @param gatewayMapper the gateway mapper
   */
  public GetAllIndividualsIndividualJpaGateway(
      IndividualRepository individualRepository, GetAllIndividualsGatewayMapper gatewayMapper) {
    this.individualRepository = individualRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public Page<IndividualDomain> findAll(
      UUID applicationId, String individualType, int page, int pageSize) {
    IndividualType typeEnum =
        individualType != null ? IndividualType.valueOf(individualType) : null;
    Specification<IndividualEntity> spec =
        IndividualSpecification.filterApplicationId(applicationId)
            .and(IndividualSpecification.filterIndividualType(typeEnum));
    Pageable pageable = createPageable(page, pageSize);
    return individualRepository.findAll(spec, pageable).map(gatewayMapper::toDomain);
  }
}
