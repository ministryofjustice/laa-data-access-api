package uk.gov.justice.laa.dstew.access.service;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.createPageable;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.wrapResult;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortFields;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSummarySpecification;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service class responsible for retrieving and managing {@link ApplicationSummary} data.
 */
@Service
public class ApplicationSummaryService {
  private final ApplicationRepository applicationRepository;
  private final CaseworkerRepository caseworkerRepository;
  private final ApplicationSummaryMapper mapper;

  /**
   * Constructs a new {@link ApplicationSummaryService} with the required repositories and mapper.
   *
   * @param applicationRepository the repository used to access application entities
   * @param applicationSummaryMapper the mapper used to convert entities into API-facing models
   * @param caseworkerRepository the repository used to access caseworker data
   */
  public ApplicationSummaryService(
      final ApplicationRepository applicationRepository,
      final ApplicationSummaryMapper applicationSummaryMapper,
      final CaseworkerRepository caseworkerRepository
  ) {
    this.applicationRepository = applicationRepository;
    this.mapper = applicationSummaryMapper;
    this.caseworkerRepository = caseworkerRepository;
  }

  /**
   * Retrieves a paginated list of {@link ApplicationSummary} objects filtered by application status.
   *
   * @param applicationStatus the {@link ApplicationStatus} used to filter results on application status
   * @param laaReference used to filter results on application reference
   * @param clientFirstName used to filter results on linked individuals first name
   * @param clientLastName used to filter results on  linked individuals last name
   * @param page the page number (one-based)
   * @param pageSize the maximum number of results to return per page
   * @return a {@link PaginatedResult} containing the page and validated pagination parameters
   */
  @AllowApiCaseworker
  public PaginatedResult<ApplicationSummary> getAllApplications(
          ApplicationStatus applicationStatus,
          String laaReference,
          String clientFirstName,
          String clientLastName,
          LocalDate clientDateOfBirth,
          UUID userId,
          Boolean isAutoGranted,
          MatterType matterType,
          ApplicationSortBy sortBy,
          ApplicationOrderBy orderBy,
          Integer page,
          Integer pageSize) {

    Pageable pageDetails = createPageable(page, pageSize, createSortAndOrderBy(sortBy, orderBy));

    if (userId != null && caseworkerRepository.countById(userId) == 0L) {
      throw new ValidationException(List.of("Caseworker not found"));
    }

    List<String> summaryProjection = Arrays.asList(
        "id", "status", "laaReference", "officeCode", "submittedAt", "modifiedAt",
        "usedDelegatedFunctions", "categoryOfLaw", "matterType", "isAutoGranted",
        "isLead", "caseworker.id", "individuals"
    );

    Page<ApplicationSummaryResult> resultPage = applicationRepository
        .findBy(ApplicationSummarySpecification
                .filterBy(applicationStatus,
                    laaReference,
                    clientFirstName,
                    clientLastName,
                    clientDateOfBirth,
                    userId,
                    matterType,
                    isAutoGranted),
            q -> q.as(ApplicationSummaryResult.class)
                .project(summaryProjection)
                .page(pageDetails));

    List<UUID> pageIds = resultPage.getContent().stream().map(ApplicationSummaryResult::getId).toList();
    List<UUID> allLeadIds = applicationRepository.findLeadIdsByAssociatedIds(pageIds);
    Map<UUID, List<LinkedApplicationSummaryDto>> linkedApplications = retrieveLinkedApplications(resultPage.getContent(), allLeadIds);

    return wrapResult(page, pageSize, resultPage.map(entity -> {
      ApplicationSummary summary = mapper.toApplicationSummary(entity);
      summary.setIsLead(allLeadIds.contains(entity.getId()));
      summary.setLinkedApplications(
          linkedApplications.getOrDefault(entity.getId(), List.of())
              .stream()
              .map(mapper::toLinkedApplicationSummary)
              .toList()
      );
      return summary;
    }));
  }

  private Map<UUID, List<LinkedApplicationSummaryDto>> retrieveLinkedApplications(List<ApplicationSummaryResult> content, List<UUID> allLeadIds) {

    if (allLeadIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, List<LinkedApplicationSummaryDto>> linkedAppsByLeadId = applicationRepository
        .findAllLinkedApplicationsByLeadIds(allLeadIds)
        .stream()
        .collect(Collectors.groupingBy(LinkedApplicationSummaryDto::getLeadApplicationId));

    return content.stream().collect(Collectors.toMap(
        ApplicationSummaryResult::getId,
        entity -> resolveLinkedApplications(entity.getId(), linkedAppsByLeadId),
        (existing, duplicate) -> existing
    ));
  }

  private List<LinkedApplicationSummaryDto> resolveLinkedApplications(
      UUID applicationId,
      Map<UUID, List<LinkedApplicationSummaryDto>> linkedAppsByLeadId) {

    List<LinkedApplicationSummaryDto> group = linkedAppsByLeadId.getOrDefault(applicationId,
        linkedAppsByLeadId.values().stream()
            .filter(linkedGroup -> linkedGroup.stream().anyMatch(dto -> dto.getApplicationId().equals(applicationId)))
            .findFirst()
            .orElse(List.of()));

    return group.stream().filter(dto -> !dto.getApplicationId().equals(applicationId)).toList();
  }

  private Sort createSortAndOrderBy(ApplicationSortBy sortBy,
                          ApplicationOrderBy orderBy) {
    ApplicationSortFields sortField = (sortBy == null)
            ? ApplicationSortFields.SUBMITTED_DATE : ApplicationSortFields.valueOf(sortBy.getValue());
    Sort.Direction direction = (orderBy == null)
            ? Sort.Direction.ASC : Sort.Direction.fromString(orderBy.getValue());

    return Sort.by(direction, sortField.getValue());
  }
}
