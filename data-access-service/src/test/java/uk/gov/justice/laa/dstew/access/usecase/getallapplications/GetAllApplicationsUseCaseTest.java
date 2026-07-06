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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationSummaryDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.LinkedApplicationSummaryDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallapplications.GetAllApplicationsCommandGenerator;
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
  void givenFullyPopulatedCommand_whenExecuted_thenReturnsCorrectResult() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(GetAllApplicationsCommandGenerator.class);
    ApplicationSummaryDomain domain =
        DataGenerator.createDefault(ApplicationSummaryDomainGenerator.class);
    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of(domain));

    when(caseworkerGateway.caseworkerExists(command.userId())).thenReturn(true);
    when(applicationGateway.findAllApplications(
            eq(command.status()),
            eq(command.laaReference()),
            eq(command.clientFirstName()),
            eq(command.clientLastName()),
            eq(command.clientDateOfBirth()),
            eq(command.userId()),
            eq(command.matterType()),
            eq(command.isAutoGranted()),
            eq(command.sortBy()),
            eq(command.orderBy()),
            eq(command.page()),
            eq(command.pageSize())))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(anyList())).thenReturn(List.of());

    GetAllApplicationsResult result = useCase.execute(command);

    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(10);
    assertThat(result.applications().getTotalElements()).isEqualTo(1);
    assertThat(result.applications().getContent().get(0))
        .usingRecursiveComparison()
        .isEqualTo(domain);
  }

  @Test
  void givenNullUserId_whenExecuted_thenSkipsCaseworkerCheck() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(GetAllApplicationsCommandGenerator.class, b -> b.userId(null));
    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of());
    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    useCase.execute(command);

    verify(caseworkerGateway, never()).caseworkerExists(any());
  }

  @Test
  void givenUserIdAndCaseworkerFound_whenExecuted_thenReturnsResult() {
    UUID userId = UUID.randomUUID();
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(userId));
    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of());

    when(caseworkerGateway.caseworkerExists(userId)).thenReturn(true);
    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(command);

    assertThat(result).isNotNull();
    verify(caseworkerGateway).caseworkerExists(userId);
  }

  @Test
  void givenUserIdAndCaseworkerNotFound_whenExecuted_thenThrowsValidationException() {
    UUID userId = UUID.randomUUID();
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(userId));

    when(caseworkerGateway.caseworkerExists(userId)).thenReturn(false);

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e -> assertThat(e.errors()).anyMatch(err -> err.contains("Caseworker not found")));

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenEmptyPage_whenExecuted_thenLinkedAppsGatewayNeverCalled() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(GetAllApplicationsCommandGenerator.class, b -> b.userId(null));
    Page<ApplicationSummaryDomain> emptyPage = new PageImpl<>(List.of());

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(emptyPage);

    GetAllApplicationsResult result = useCase.execute(command);

    assertThat(result.applications().isEmpty()).isTrue();
    verify(applicationGateway, never()).findLinkedApplicationsForPageIds(anyList());
  }

  @Test
  void givenLeadApplication_whenExecuted_thenLinkedApplicationsResolvedCorrectly() {
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();

    ApplicationSummaryDomain lead =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.id(leadId).isLead(true));

    LinkedApplicationSummaryDomain associateLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDomainGenerator.class,
            b -> b.applicationId(associateId).isLead(false).leadApplicationId(leadId));

    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of(lead));

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(List.of(leadId)))
        .thenReturn(List.of(associateLink));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsCommandGenerator.class, b -> b.userId(null)));

    List<LinkedApplicationSummaryDomain> linked =
        result.applications().getContent().get(0).linkedApplications();
    assertThat(linked).hasSize(1);
    assertThat(linked.get(0).applicationId()).isEqualTo(associateId);
  }

  @Test
  void givenAssociateApplication_whenExecuted_thenLinkedApplicationsResolvedFromLeadGroup() {
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();

    ApplicationSummaryDomain associate =
        DataGenerator.createDefault(
            ApplicationSummaryDomainGenerator.class, b -> b.id(associateId).isLead(false));

    LinkedApplicationSummaryDomain leadLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDomainGenerator.class,
            b -> b.applicationId(leadId).isLead(true).leadApplicationId(leadId));
    LinkedApplicationSummaryDomain associateLink =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDomainGenerator.class,
            b -> b.applicationId(associateId).isLead(false).leadApplicationId(leadId));

    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of(associate));

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(List.of(associateId)))
        .thenReturn(List.of(leadLink, associateLink));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsCommandGenerator.class, b -> b.userId(null)));

    List<LinkedApplicationSummaryDomain> linked =
        result.applications().getContent().get(0).linkedApplications();
    assertThat(linked).hasSize(1);
    assertThat(linked.get(0).applicationId()).isEqualTo(leadId);
  }

  @Test
  void givenApplicationsFromDifferentGroups_whenExecuted_thenGroupsAreIsolated() {
    UUID leadA = UUID.randomUUID();
    UUID leadB = UUID.randomUUID();
    UUID assocA = UUID.randomUUID();

    ApplicationSummaryDomain appA =
        DataGenerator.createDefault(ApplicationSummaryDomainGenerator.class, b -> b.id(leadA));
    ApplicationSummaryDomain appB =
        DataGenerator.createDefault(ApplicationSummaryDomainGenerator.class, b -> b.id(leadB));

    LinkedApplicationSummaryDomain linkA =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDomainGenerator.class,
            b -> b.applicationId(assocA).isLead(false).leadApplicationId(leadA));

    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of(appA, appB));

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);
    when(applicationGateway.findLinkedApplicationsForPageIds(anyList())).thenReturn(List.of(linkA));

    GetAllApplicationsResult result =
        useCase.execute(
            DataGenerator.createDefault(
                GetAllApplicationsCommandGenerator.class, b -> b.userId(null)));

    List<ApplicationSummaryDomain> content = result.applications().getContent();
    ApplicationSummaryDomain resultA =
        content.stream().filter(d -> d.id().equals(leadA)).findFirst().orElseThrow();
    ApplicationSummaryDomain resultB =
        content.stream().filter(d -> d.id().equals(leadB)).findFirst().orElseThrow();
    assertThat(resultA.linkedApplications()).hasSize(1);
    assertThat(resultA.linkedApplications().get(0).applicationId()).isEqualTo(assocA);
    assertThat(resultB.linkedApplications()).isEmpty();
  }

  @Test
  void givenInvalidPage_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(null).page(0));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("page must be greater than or equal to 1");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenInvalidPageSize_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(null).pageSize(0));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("pageSize must be greater than or equal to 1");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenPageSizeExceedingMax_whenExecuted_thenThrowsIllegalArgumentException() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(null).pageSize(101));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("pageSize cannot be more than 100");

    verify(applicationGateway, never())
        .findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void givenMaximumPageSize_whenExecuted_thenReturnsResult() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class, b -> b.userId(null).pageSize(100));
    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of());

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(command);

    assertThat(result.requestedPageSize()).isEqualTo(100);
  }

  @Test
  void givenDefaultPagination_whenExecuted_thenValidatedDefaultsAreInResult() {
    GetAllApplicationsCommand command =
        DataGenerator.createDefault(
            GetAllApplicationsCommandGenerator.class,
            b -> b.userId(null).page(null).pageSize(null));
    Page<ApplicationSummaryDomain> page = new PageImpl<>(List.of());

    when(applicationGateway.findAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    GetAllApplicationsResult result = useCase.execute(command);

    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
  }
}
