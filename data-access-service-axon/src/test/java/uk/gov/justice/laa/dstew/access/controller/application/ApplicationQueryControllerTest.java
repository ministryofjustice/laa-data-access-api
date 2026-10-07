package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationDetailResult;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.LinkedApplicationMemberDetails;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/** Verifies the branch logic in ApplicationQueryController that integration tests do not reach. */
@ExtendWith(MockitoExtension.class)
class ApplicationQueryControllerTest {

  @Mock private ApplicationQueryUseCase applicationQueryUseCase;

  @Mock private GetApplicationResponseMapper responseMapper;

  @Mock private GetAllApplicationsResponseMapper getAllResponseMapper;

  @Mock private GetApplicationHistoryResponseMapper historyResponseMapper;

  @Mock private GetAllNotesForApplicationResponseMapper notesResponseMapper;

  @InjectMocks private ApplicationQueryController controller;

  @Test
  void givenApplicationDetail_whenGetApplicationById_thenMapsCombinedResult() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application = new ApplicationReadModel();
    LinkedApplicationGroupReadModel linkedGroup = new LinkedApplicationGroupReadModel();
    List<PriorAuthorityReadModel> priorAuthorities = List.of(new PriorAuthorityReadModel());
    Map<UUID, LinkedApplicationMemberDetails> linkedMemberDetails =
        Map.of(
            UUID.randomUUID(), new LinkedApplicationMemberDetails("LAA-LINKED", "Grace", "Hopper"));
    ApplicationDetailResult detail =
        new ApplicationDetailResult(
            application, linkedGroup, priorAuthorities, linkedMemberDetails);
    ApplicationResponse expectedResponse = new ApplicationResponse();
    when(applicationQueryUseCase.getApplicationDetail(applicationId)).thenReturn(detail);
    when(responseMapper.toResponse(application, linkedGroup, priorAuthorities, linkedMemberDetails))
        .thenReturn(expectedResponse);

    var response = controller.getApplicationById(ServiceName.CIVIL_APPLY, applicationId);

    assertThat(response.getBody()).isSameAs(expectedResponse);
    verify(applicationQueryUseCase).getApplicationDetail(applicationId);
    verify(responseMapper)
        .toResponse(application, linkedGroup, priorAuthorities, linkedMemberDetails);
  }

  @Test
  void givenNullEventType_whenGetApplicationHistory_thenAllEventTypesRequested() {
    UUID id = UUID.randomUUID();
    ApplicationHistoryResult history = new ApplicationHistoryResult(List.of(), List.of());
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
    ApplicationHistoryResult history = new ApplicationHistoryResult(List.of(), List.of());
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
    ApplicationHistoryResult history = new ApplicationHistoryResult(List.of(), List.of());
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
  void givenMatterTypeCode_whenGetApplications_thenPassesExactCode() {
    FindAllApplicationsResult result =
        new FindAllApplicationsResult(List.of(), Map.of(), Map.of(), Map.of(), 0, 1, 20);
    when(applicationQueryUseCase.getApplications(any(FindAllApplicationsQuery.class)))
        .thenReturn(result);
    when(getAllResponseMapper.toResponse(result))
        .thenReturn(ResponseEntity.ok(new ApplicationSummaryResponse()));
    controller.getApplications(
        ServiceName.CIVIL_APPLY,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "MAT-123",
        ApplicationSortBy.SUBMITTED_DATE,
        ApplicationOrderBy.ASC,
        null,
        null);

    var query = ArgumentCaptor.forClass(FindAllApplicationsQuery.class);
    verify(applicationQueryUseCase).getApplications(query.capture());
    assertThat(query.getValue().matterTypeCode()).isEqualTo("MAT-123");
  }
}
