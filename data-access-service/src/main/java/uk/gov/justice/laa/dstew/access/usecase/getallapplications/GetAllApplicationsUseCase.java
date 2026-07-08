package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.LinkedApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.PagedResult;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Retrieves a paginated list of application summaries. */
public class GetAllApplicationsUseCase {

  private final GetAllApplicationsApplicationGateway applicationGateway;
  private final GetAllApplicationsCaseworkerGateway caseworkerGateway;

  /**
   * Constructs the use case with the required gateways.
   *
   * @param applicationGateway the gateway for paginated application queries
   * @param caseworkerGateway the gateway for caseworker existence checks
   */
  public GetAllApplicationsUseCase(
      GetAllApplicationsApplicationGateway applicationGateway,
      GetAllApplicationsCaseworkerGateway caseworkerGateway) {
    this.applicationGateway = applicationGateway;
    this.caseworkerGateway = caseworkerGateway;
  }

  /**
   * Retrieves a page of application summaries.
   *
   * @param query the input query
   * @return the paged application summaries and pagination metadata
   */
  @AllowApiCaseworker
  public GetAllApplicationsResult execute(GetAllApplicationsQuery query) {
    int validatedPage = PaginationHelper.validatePage(query.page());
    int validatedPageSize = PaginationHelper.validatePageSize(query.pageSize());

    if (query.userId() != null && !caseworkerGateway.caseworkerExists(query.userId())) {
      throw new ValidationException(List.of("Caseworker not found"));
    }

    PagedResult<ApplicationSummaryReadModel> page =
        applicationGateway.findAllApplications(
            query.status(),
            query.laaReference(),
            query.clientFirstName(),
            query.clientLastName(),
            query.clientDateOfBirth(),
            query.userId(),
            query.matterType(),
            query.isAutoGranted(),
            query.sortBy(),
            query.orderBy(),
            query.page(),
            query.pageSize());

    PagedResult<ApplicationSummaryReadModel> resolvedPage = page;
    if (!page.content().isEmpty()) {
      List<UUID> pageIds = page.content().stream().map(ApplicationSummaryReadModel::id).toList();
      List<LinkedApplicationSummaryReadModel> allLinked =
          applicationGateway.findLinkedApplicationsForPageIds(pageIds);
      Map<UUID, List<LinkedApplicationSummaryReadModel>> byLeadId =
          allLinked.stream()
              .collect(Collectors.groupingBy(LinkedApplicationSummaryReadModel::leadApplicationId));
      List<ApplicationSummaryReadModel> resolved =
          page.content().stream()
              .map(
                  domain ->
                      domain.toBuilder()
                          .linkedApplications(resolveLinkedApplications(domain.id(), byLeadId))
                          .build())
              .toList();
      resolvedPage = new PagedResult<>(resolved, page.totalElements());
    }

    return new GetAllApplicationsResult(resolvedPage, validatedPage, validatedPageSize);
  }

  private List<LinkedApplicationSummaryReadModel> resolveLinkedApplications(
      UUID applicationId, Map<UUID, List<LinkedApplicationSummaryReadModel>> byLeadId) {
    List<LinkedApplicationSummaryReadModel> group =
        byLeadId.getOrDefault(
            applicationId,
            byLeadId.values().stream()
                .filter(
                    linkedGroup ->
                        linkedGroup.stream()
                            .anyMatch(dto -> dto.applicationId().equals(applicationId)))
                .findFirst()
                .orElse(List.of()));
    return group.stream().filter(dto -> !dto.applicationId().equals(applicationId)).toList();
  }
}
