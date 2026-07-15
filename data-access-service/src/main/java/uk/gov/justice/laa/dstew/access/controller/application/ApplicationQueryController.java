package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.ApplicationQueryApi;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.applications.SdsService;
import uk.gov.justice.laa.dstew.access.service.domainevents.GetDomainEventService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.GetAllNotesForApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.GetApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.GetCertificateUseCase;

/** Controller for handling /api/v0/applications query requests. */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class ApplicationQueryController implements ApplicationQueryApi {

  private final GetApplicationUseCase getApplicationUseCase;
  private final GetApplicationResponseMapper getApplicationResponseMapper;
  private final GetAllNotesForApplicationUseCase getAllNotesForApplicationUseCase;
  private final GetAllNotesForApplicationResponseMapper getAllNotesForApplicationResponseMapper;
  private final GetDomainEventService getDomainEventsService;
  private final SdsService sdsService;
  private final GetAllApplicationsQueryMapper getAllApplicationsQueryMapper;
  private final GetAllApplicationsResponseMapper getAllApplicationsResponseMapper;
  private final GetAllApplicationsUseCase getAllApplicationsUseCase;
  private final GetCertificateUseCase getCertificateUseCase;

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<ApplicationSummaryResponse> getApplications(
      ServiceName serviceName,
      ApplicationStatus status,
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

    return getAllApplicationsResponseMapper.toGetAllApplicationsResponse(
        getAllApplicationsUseCase.execute(
            getAllApplicationsQueryMapper.toGetAllApplicationsQuery(
                status,
                laaReference,
                clientFirstName,
                clientLastName,
                clientDateOfBirth,
                userId,
                isAutoGranted,
                matterType,
                sortBy,
                orderBy,
                page,
                pageSize)));
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<ApplicationResponse> getApplicationById(ServiceName serviceName, UUID id) {
    return getApplicationResponseMapper.toGetApplicationResponse(getApplicationUseCase.execute(id));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(
      @NotNull ServiceName serviceName,
      UUID applicationId,
      @Valid List<DomainEventType> eventType) {
    var events = getDomainEventsService.getEvents(applicationId, eventType);
    return ResponseEntity.ok(ApplicationHistoryResponse.builder().events(events).build());
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationNotesResponse> getApplicationNotes(
      @NotNull ServiceName serviceName, UUID applicationId) {
    return ResponseEntity.ok(
        getAllNotesForApplicationResponseMapper.toResponse(
            getAllNotesForApplicationUseCase.execute(applicationId)));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Map<String, Object>> getCertificate(
      @NotNull ServiceName serviceName, UUID applicationId) {
    return ResponseEntity.ok(getCertificateUseCase.execute(applicationId).certificateContent());
  }

  @Hidden
  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<DocumentDownloadResponse> downloadDocument(
      @NotNull ServiceName serviceName, UUID id, String documentId) {
    return ResponseEntity.ok(sdsService.getFile(id, documentId));
  }
}
