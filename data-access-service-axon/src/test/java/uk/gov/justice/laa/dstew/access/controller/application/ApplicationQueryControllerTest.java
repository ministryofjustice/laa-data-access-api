package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/** Verifies the branch logic in ApplicationQueryController that integration tests do not reach. */
class ApplicationQueryControllerTest {

  private ApplicationQueryUseCase applicationQueryUseCase;
  private GetApplicationHistoryResponseMapper historyResponseMapper;
  private ApplicationQueryController controller;

  @BeforeEach
  void setUp() {
    applicationQueryUseCase = mock(ApplicationQueryUseCase.class);
    historyResponseMapper = mock(GetApplicationHistoryResponseMapper.class);
    controller =
        new ApplicationQueryController(
            applicationQueryUseCase,
            mock(GetApplicationResponseMapper.class),
            mock(GetAllApplicationsResponseMapper.class),
            historyResponseMapper,
            mock(GetAllNotesForApplicationResponseMapper.class),
            mock(SubscriptionProjectionGateway.class));
  }

  @Test
  void givenNullEventType_whenGetApplicationHistory_thenAllEventTypesRequested() {
    UUID id = UUID.randomUUID();
    List<ApplicationHistoryReadModel> history = List.of();
    when(applicationQueryUseCase.getApplicationHistory(eq(id), any())).thenReturn(history);
    when(historyResponseMapper.toResponse(history)).thenReturn(new ApplicationHistoryResponse());

    controller.getApplicationHistory(ServiceName.CIVIL_APPLY, id, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(applicationQueryUseCase).getApplicationHistory(eq(id), captor.capture());
    List<String> expectedTypes =
        Arrays.stream(DomainEventType.values()).map(DomainEventType::getValue).toList();
    assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(expectedTypes);
  }

  @Test
  void givenEmptyEventType_whenGetApplicationHistory_thenAllEventTypesRequested() {
    UUID id = UUID.randomUUID();
    List<ApplicationHistoryReadModel> history = List.of();
    when(applicationQueryUseCase.getApplicationHistory(eq(id), any())).thenReturn(history);
    when(historyResponseMapper.toResponse(history)).thenReturn(new ApplicationHistoryResponse());

    controller.getApplicationHistory(ServiceName.CIVIL_APPLY, id, List.of());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(applicationQueryUseCase).getApplicationHistory(eq(id), captor.capture());
    List<String> expectedTypes =
        Arrays.stream(DomainEventType.values()).map(DomainEventType::getValue).toList();
    assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(expectedTypes);
  }

  @Test
  void givenNonEmptyEventType_whenGetApplicationHistory_thenOnlyRequestedTypesUsed() {
    UUID id = UUID.randomUUID();
    List<ApplicationHistoryReadModel> history = List.of();
    when(applicationQueryUseCase.getApplicationHistory(eq(id), any())).thenReturn(history);
    when(historyResponseMapper.toResponse(history)).thenReturn(new ApplicationHistoryResponse());

    controller.getApplicationHistory(
        ServiceName.CIVIL_APPLY, id, List.of(DomainEventType.APPLICATION_CREATED));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(applicationQueryUseCase).getApplicationHistory(eq(id), captor.capture());
    assertThat(captor.getValue()).containsExactly(DomainEventType.APPLICATION_CREATED.getValue());
  }

  @Test
  void givenNonNullMatterTypeSortByOrderBy_whenGetApplications_thenCallsUseCaseWithNames() {
    controller.getApplications(
        ServiceName.CIVIL_APPLY,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        MatterType.SPECIAL_CHILDREN_ACT,
        ApplicationSortBy.SUBMITTED_DATE,
        ApplicationOrderBy.ASC,
        null,
        null);
  }
}
