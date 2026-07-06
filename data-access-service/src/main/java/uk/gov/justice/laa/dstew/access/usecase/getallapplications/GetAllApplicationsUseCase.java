package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Use case responsible for retrieving a paginated list of application summaries. Validates
 * caseworker existence before querying applications, and resolves linked-application relationships
 * in pure domain logic after the page query.
 */
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
   * Executes the getAllApplications use case.
   *
   * <p>Execution order: (1) validate pagination parameters; (2) validate caseworker existence when
   * {@code userId} is supplied; (3) fetch the application page; (4) if non-empty, fetch and resolve
   * linked applications; (5) return the result.
   *
   * @param command the input command carrying all filter, sort, and pagination parameters
   * @return a {@link GetAllApplicationsResult} containing the paged summaries and pagination
   *     metadata
   */
  @AllowApiCaseworker
  public GetAllApplicationsResult execute(GetAllApplicationsCommand command) {
    int validatedPage = PaginationHelper.validatePage(command.page());
    int validatedPageSize = PaginationHelper.validatePageSize(command.pageSize());

    if (command.userId() != null && !caseworkerGateway.caseworkerExists(command.userId())) {
      throw new ValidationException(List.of("Caseworker not found"));
    }

    Page<ApplicationSummaryDomain> page =
        applicationGateway.findAllApplications(
            command.status(),
            command.laaReference(),
            command.clientFirstName(),
            command.clientLastName(),
            command.clientDateOfBirth(),
            command.userId(),
            command.matterType(),
            command.isAutoGranted(),
            command.sortBy(),
            command.orderBy(),
            command.page(),
            command.pageSize());

    Page<ApplicationSummaryDomain> resolvedPage = page;
    if (!page.isEmpty()) {
      List<UUID> pageIds = page.getContent().stream().map(ApplicationSummaryDomain::id).toList();
      List<LinkedApplicationSummaryDomain> allLinked =
          applicationGateway.findLinkedApplicationsForPageIds(pageIds);
      Map<UUID, List<LinkedApplicationSummaryDomain>> byLeadId =
          allLinked.stream()
              .collect(Collectors.groupingBy(LinkedApplicationSummaryDomain::leadApplicationId));
      resolvedPage =
          page.map(
              domain ->
                  domain.toBuilder()
                      .linkedApplications(resolveLinkedApplications(domain.id(), byLeadId))
                      .build());
    }

    return new GetAllApplicationsResult(resolvedPage, validatedPage, validatedPageSize);
  }

  private List<LinkedApplicationSummaryDomain> resolveLinkedApplications(
      UUID applicationId, Map<UUID, List<LinkedApplicationSummaryDomain>> byLeadId) {
    List<LinkedApplicationSummaryDomain> group =
        byLeadId.getOrDefault(
            applicationId,
            byLeadId.values().stream()
                .filter(g -> g.stream().anyMatch(d -> d.applicationId().equals(applicationId)))
                .findFirst()
                .orElse(List.of()));
    return group.stream().filter(d -> !d.applicationId().equals(applicationId)).toList();
  }
}
