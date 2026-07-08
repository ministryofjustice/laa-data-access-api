package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.LinkedApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.PagedResult;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallapplications.ApplicationSummaryReadModelGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallapplications.GetAllApplicationsQueryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallapplications.LinkedApplicationSummaryReadModelGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class GetAllApplicationsUseCaseTest {

  @Mock private GetAllApplicationsApplicationGateway applicationGateway;
  @Mock private GetAllApplicationsCaseworkerGateway caseworkerGateway;

  private GetAllApplicationsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetAllApplicationsUseCase(applicationGateway, caseworkerGateway);
  }

  @Test
  void givenFullyPopulatedQuery_whenExecuted_thenReturnsCorrectResult() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(GetAllApplicationsQueryGenerator.class);
    ApplicationSummaryReadModel domain =
        DataGenerator.createDefault(ApplicationSummaryReadModelGenerator.class);
    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(domain), 1);

    when(caseworkerGateway.caseworkerExists(query.userId())).thenReturn(true);
    when(applicationGateway.findAllApplications(
            eq(query.status()),
            eq(query.laaReference()),
            eq(query.clientFirstName()),
            eq(query.clientLastName()),
            eq(query.clientDateOfBirth()),
            eq(query.userId()),
            eq(query.matterType()),
            eq(query.isAutoGranted()),
            eq(query.sortBy()),
            eq(query.orderBy()),
            eq(query.page()),
            eq(query.pageSize())))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(anyList())).thenReturn(List.of());

    GetAllApplicationsResult result = useCase.execute(query);

    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(10);
    assertThat(result.applications().totalElements()).isEqualTo(1);
    assertThat(result.applications().content().get(0)).usingRecursiveComparison().isEqualTo(domain);
  }

  @Test
  void givenNullUserId_whenExecuted_thenSkipsCaseworkerCheck() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(GetAllApplicationsQueryGenerator.class, b -> b.userId(null));
    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(), 0);
    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    useCase.execute(query);

    verify(caseworkerGateway, never()).caseworkerExists(any());
  }

  @Test
  void givenUserIdAndCaseworkerFound_whenExecuted_thenReturnsResult() {
    UUID userId = UUID.randomUUID();
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(GetAllApplicationsQueryGenerator.class, b -> b.userId(userId));
    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(), 0);

    when(caseworkerGateway.caseworkerExists(userId)).thenReturn(true);
    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(query);

    assertThat(result).isNotNull();
    verify(caseworkerGateway).caseworkerExists(userId);
  }

  @Test
  void givenUserIdAndCaseworkerNotFound_whenExecuted_thenThrowsValidationException() {
    UUID userId = UUID.randomUUID();
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(GetAllApplicationsQueryGenerator.class, b -> b.userId(userId));

    when(caseworkerGateway.caseworkerExists(userId)).thenReturn(false);

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(query))
        .satisfies(
            e -> assertThat(e.errors()).anyMatch(err -> err.contains("Caseworker not found")));

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenEmptyPage_whenExecuted_thenLinkedAppsGatewayNeverCalled() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(GetAllApplicationsQueryGenerator.class, b -> b.userId(null));
    PagedResult<ApplicationSummaryReadModel> emptyPage = new PagedResult<>(List.of(), 0);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(emptyPage);

    GetAllApplicationsResult result = useCase.execute(query);

    assertThat(result.applications().content().isEmpty()).isTrue();
    verify(applicationGateway, never()).findLinkedApplicationsForPageIds(anyList());
  }

  @Test
  void givenLeadApplication_whenExecuted_thenLinkedApplicationsResolvedCorrectly() {
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();

    ApplicationSummaryReadModel lead =
        DataGenerator.createDefault(
            ApplicationSummaryReadModelGenerator.class, b -> b.id(leadId).isLead(true));

    LinkedApplicationSummaryReadModel associateLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryReadModelGenerator.class,
            b -> b.applicationId(associateId).isLead(false).leadApplicationId(leadId));

    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(lead), 1);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(List.of(leadId)))
        .thenReturn(List.of(associateLink));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsQueryGenerator.class, b -> b.userId(null)));

    List<LinkedApplicationSummaryReadModel> linked =
        result.applications().content().get(0).linkedApplications();
    assertThat(linked).hasSize(1);
    assertThat(linked.get(0).applicationId()).isEqualTo(associateId);
  }

  @Test
  void givenAssociateApplication_whenExecuted_thenLinkedApplicationsResolvedFromLeadGroup() {
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();

    ApplicationSummaryReadModel associate =
        DataGenerator.createDefault(
            ApplicationSummaryReadModelGenerator.class, b -> b.id(associateId).isLead(false));

    LinkedApplicationSummaryReadModel leadLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryReadModelGenerator.class,
            b -> b.applicationId(leadId).isLead(true).leadApplicationId(leadId));
    LinkedApplicationSummaryReadModel associateLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryReadModelGenerator.class,
            b -> b.applicationId(associateId).isLead(false).leadApplicationId(leadId));

    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(associate), 1);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(List.of(associateId)))
        .thenReturn(List.of(leadLink, associateLink));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsQueryGenerator.class, b -> b.userId(null)));

    List<LinkedApplicationSummaryReadModel> linked =
        result.applications().content().get(0).linkedApplications();
    assertThat(linked).hasSize(1);
    assertThat(linked.get(0).applicationId()).isEqualTo(leadId);
  }

  @Test
  void givenApplicationsFromDifferentGroups_whenExecuted_thenGroupsAreIsolated() {
    UUID leadA = UUID.randomUUID();
    UUID leadB = UUID.randomUUID();
    UUID assocA = UUID.randomUUID();

    ApplicationSummaryReadModel appA =
        DataGenerator.createDefault(ApplicationSummaryReadModelGenerator.class, b -> b.id(leadA));
    ApplicationSummaryReadModel appB =
        DataGenerator.createDefault(ApplicationSummaryReadModelGenerator.class, b -> b.id(leadB));

    LinkedApplicationSummaryReadModel linkA =
        DataGenerator.createDefault(
            LinkedApplicationSummaryReadModelGenerator.class,
            b -> b.applicationId(assocA).isLead(false).leadApplicationId(leadA));

    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(appA, appB), 2);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(anyList())).thenReturn(List.of(linkA));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsQueryGenerator.class, b -> b.userId(null)));

    List<ApplicationSummaryReadModel> content = result.applications().content();
    ApplicationSummaryReadModel resultA =
        content.stream().filter(d -> d.id().equals(leadA)).findFirst().orElseThrow();
    ApplicationSummaryReadModel resultB =
        content.stream().filter(d -> d.id().equals(leadB)).findFirst().orElseThrow();
    assertThat(resultA.linkedApplications()).hasSize(1);
    assertThat(resultA.linkedApplications().get(0).applicationId()).isEqualTo(assocA);
    assertThat(resultB.linkedApplications()).isEmpty();
  }

  @Test
  void givenInvalidPage_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(
            GetAllApplicationsQueryGenerator.class, b -> b.userId(null).page(0));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(query))
        .withMessageContaining("page must be greater than or equal to 1");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenInvalidPageSize_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(
            GetAllApplicationsQueryGenerator.class, b -> b.userId(null).pageSize(0));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(query))
        .withMessageContaining("pageSize must be greater than or equal to 1");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenPageSizeExceedingMax_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(
            GetAllApplicationsQueryGenerator.class, b -> b.userId(null).pageSize(101));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(query))
        .withMessageContaining("pageSize cannot be more than 100");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenMaximumPageSize_whenExecuted_thenReturnsResult() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(
            GetAllApplicationsQueryGenerator.class, b -> b.userId(null).pageSize(100));
    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(), 0);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(query);

    assertThat(result.requestedPageSize()).isEqualTo(100);
  }

  @Test
  void givenDefaultPagination_whenExecuted_thenValidatedDefaultsAreInResult() {
    GetAllApplicationsQuery query =
        DataGenerator.createDefault(
            GetAllApplicationsQueryGenerator.class, b -> b.userId(null).page(null).pageSize(null));
    PagedResult<ApplicationSummaryReadModel> page = new PagedResult<>(List.of(), 0);

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(query);

    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
  }
}
