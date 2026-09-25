package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationDetailResult;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/** Verifies the branch logic in ApplicationQueryController that integration tests do not reach. */
@ExtendWith(MockitoExtension.class)
class ApplicationQueryControllerTest {

  @Mock private ApplicationQueryUseCase applicationQueryUseCase;
  @Mock private GetApplicationResponseMapper responseMapper;
  @Mock private GetAllApplicationsResponseMapper getAllResponseMapper;
  @Mock private GetApplicationHistoryResponseMapper historyResponseMapper;
  @Mock private GetAllNotesForApplicationResponseMapper notesResponseMapper;
  @Mock private SdsService sdsService;
  private ApplicationQueryController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ApplicationQueryController(
            applicationQueryUseCase,
            responseMapper,
            getAllResponseMapper,
            historyResponseMapper,
            notesResponseMapper,
            sdsService);
  }

  @Test
  void givenApplicationDetail_whenGetApplicationById_thenMapsCombinedResult() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application = new ApplicationReadModel();
    LinkedApplicationGroupReadModel linkedGroup = new LinkedApplicationGroupReadModel();
    List<PriorAuthorityReadModel> priorAuthorities = List.of(new PriorAuthorityReadModel());
    ApplicationDetailResult detail =
        new ApplicationDetailResult(application, linkedGroup, priorAuthorities);
    ApplicationResponse expectedResponse = new ApplicationResponse();
    when(applicationQueryUseCase.getApplicationDetail(applicationId)).thenReturn(detail);
    when(responseMapper.toResponse(application, linkedGroup, priorAuthorities))
        .thenReturn(expectedResponse);

    var response = controller.getApplicationById(ServiceName.CIVIL_APPLY, applicationId);

    assertThat(response.getBody()).isSameAs(expectedResponse);
    verify(applicationQueryUseCase).getApplicationDetail(applicationId);
    verify(responseMapper).toResponse(application, linkedGroup, priorAuthorities);
  }

  @Test
  void givenDocumentExists_whenDownloadDocument_thenReturnDownloadedDocument() {
    UUID applicationId = UUID.randomUUID();
    String documentId = "document-id";
    DocumentDownloadResponse expectedResponse = new DocumentDownloadResponse();
    when(sdsService.getFile(applicationId, documentId)).thenReturn(expectedResponse);

    var response = controller.downloadDocument(ServiceName.CIVIL_APPLY, applicationId, documentId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(expectedResponse);
    verify(sdsService).getFile(applicationId, documentId);
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
