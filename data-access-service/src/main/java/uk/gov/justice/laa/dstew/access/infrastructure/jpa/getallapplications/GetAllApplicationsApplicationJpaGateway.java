package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.PagedResultDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortFields;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSummarySpecification;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper;

/**
 * JPA gateway implementation for paginated application queries in the getAllApplications use case.
 * Constructs {@link org.springframework.data.jpa.domain.Specification} and {@link Pageable}
 * internally from the plain-String command parameters.
 */
public class GetAllApplicationsApplicationJpaGateway
    implements GetAllApplicationsApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final GetAllApplicationsGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway with the required repository and mapper.
   *
   * @param applicationRepository the repository for application queries
   * @param gatewayMapper the mapper for converting DTOs to domain records
   */
  public GetAllApplicationsApplicationJpaGateway(
      ApplicationRepository applicationRepository, GetAllApplicationsGatewayMapper gatewayMapper) {
    this.applicationRepository = applicationRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public PagedResultDomain<ApplicationSummaryDomain> findAllApplications(
      String status,
      String laaReference,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      String matterType,
      Boolean isAutoGranted,
      String sortBy,
      String orderBy,
      Integer page,
      Integer pageSize) {
    Pageable pageable =
        PaginationHelper.createPageable(page, pageSize, createSortAndOrderBy(sortBy, orderBy));

    ApplicationStatus statusEnum = status != null ? ApplicationStatus.valueOf(status) : null;
    MatterType matterTypeEnum = matterType != null ? MatterType.valueOf(matterType) : null;

    Page<uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto> resultPage =
        applicationRepository.findAllAsDtos(
            ApplicationSummarySpecification.filterBy(
                statusEnum,
                laaReference,
                clientFirstName,
                clientLastName,
                clientDateOfBirth,
                userId,
                matterTypeEnum,
                isAutoGranted),
            pageable,
            clientFirstName,
            clientLastName,
            clientDateOfBirth);

    List<ApplicationSummaryDomain> content =
        resultPage.map(gatewayMapper::toApplicationSummaryDomain).getContent();
    return new PagedResultDomain<>(content, resultPage.getTotalElements());
  }

  @Override
  public List<LinkedApplicationSummaryDomain> findLinkedApplicationsForPageIds(List<UUID> pageIds) {
    return applicationRepository.findAllLinkedApplicationsForPageIds(pageIds).stream()
        .map(gatewayMapper::toLinkedApplicationSummaryDomain)
        .toList();
  }

  private Sort createSortAndOrderBy(String sortBy, String orderBy) {
    ApplicationSortFields sortField =
        (sortBy == null)
            ? ApplicationSortFields.SUBMITTED_DATE
            : ApplicationSortFields.valueOf(sortBy);
    Sort.Direction direction =
        (orderBy == null) ? Sort.Direction.ASC : Sort.Direction.fromString(orderBy);
    return Sort.by(direction, sortField.getValue());
  }
}
