package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.ApplicationQueryApi;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
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
import uk.gov.justice.laa.dstew.access.query.application.ApplicationNotesResult;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/** HTTP query adapter for Application reads. */
@RestController
public class ApplicationQueryController implements ApplicationQueryApi {

  private final ApplicationQueryUseCase applicationQueryUseCase;
  private final GetApplicationResponseMapper responseMapper;
  private final GetAllApplicationsResponseMapper getAllResponseMapper;
  private final GetApplicationHistoryResponseMapper historyResponseMapper;
  private final GetAllNotesForApplicationResponseMapper notesResponseMapper;

  /**
   * Constructs the controller with its query gateway and response mappers.
   *
   * @param applicationQueryUseCase secured application read use case
   * @param responseMapper maps a single {@link ApplicationReadModel} to {@link
   *     uk.gov.justice.laa.dstew.access.model.ApplicationResponse}
   * @param getAllResponseMapper maps application summaries to {@link
   *     uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse}
   * @param historyResponseMapper maps a list of {@link
   *     uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel} to
   *     {@link uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse}
   * @param notesResponseMapper maps notes to {@link ApplicationNotesResponse}
   */
  public ApplicationQueryController(
      ApplicationQueryUseCase applicationQueryUseCase,
      GetApplicationResponseMapper responseMapper,
      GetAllApplicationsResponseMapper getAllResponseMapper,
      GetApplicationHistoryResponseMapper historyResponseMapper,
      GetAllNotesForApplicationResponseMapper notesResponseMapper,
      SubscriptionProjectionGateway projectionGateway) {
    this.applicationQueryUseCase = applicationQueryUseCase;
    this.responseMapper = responseMapper;
    this.getAllResponseMapper = getAllResponseMapper;
    this.historyResponseMapper = historyResponseMapper;
    this.notesResponseMapper = notesResponseMapper;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Returns a paginated, filtered list of Application summaries.
   *
   * <p>Filters on {@code status}, {@code laaReference}, {@code matterType}, and {@code autoGranted}
   * are applied. {@code clientFirstName}, {@code clientLastName}, {@code clientDateOfBirth}, and
   * {@code userId} are accepted for API compatibility but not yet used as filters — a future
   * migration will denormalise client fields from the {@code individuals} JSON column to enable
   * them.
   */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationSummaryResponse> getApplications(
      ServiceName serviceName,
      ApplicationStatus status,
      String laaReference,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      uk.gov.justice.laa.dstew.access.model.AutoGranted autoGranted,
      MatterType matterType,
      ApplicationSortBy sortBy,
      ApplicationOrderBy orderBy,
      Integer page,
      Integer pageSize) {
    FindAllApplicationsResult result =
        applicationQueryUseCase.getApplications(
            new FindAllApplicationsQuery(
                status == null ? null : status.name(),
                laaReference,
                matterType == null ? null : matterType.name(),
                clientFirstName,
                clientLastName,
                clientDateOfBirth,
                autoGranted == null ? null : AutoGrantedState.valueOf(autoGranted.name()),
                sortBy == null ? null : sortBy.name(),
                orderBy == null ? null : orderBy.name(),
                page,
                pageSize));
    return getAllResponseMapper.toResponse(result);
  }

  /** Returns the current-state projection for the requested Application. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationResponse> getApplicationById(ServiceName serviceName, UUID id) {
    ApplicationReadModel application =
        findApplication(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with ID: " + id));
    return ResponseEntity.ok(responseMapper.toResponse(application));
  }

  /** Returns the certificate stored in the Application's current immutable data version. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Map<String, Object>> getCertificate(ServiceName serviceName, UUID id) {
    return ResponseEntity.ok(applicationQueryUseCase.getCertificate(id));
  }

  /** Returns domain-event history for the requested Application. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(
      ServiceName serviceName, UUID id, List<DomainEventType> eventType) {
    List<String> requestedTypes =
        (eventType == null || eventType.isEmpty())
            ? Arrays.stream(DomainEventType.values()).map(DomainEventType::getValue).toList()
            : eventType.stream().map(DomainEventType::getValue).toList();
    List<ApplicationHistoryReadModel> history =
        applicationQueryUseCase.getApplicationHistory(id, requestedTypes);
    return ResponseEntity.ok(historyResponseMapper.toResponse(history));
  }

  /** Returns all notes for the requested Application, ordered by creation time ascending. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationNotesResponse> getApplicationNotes(
      ServiceName serviceName, UUID id) {
    ApplicationNotesResponse response =
        notesResponseMapper.toResponse(applicationQueryUseCase.getNotesForApplication(id).notes());
    return ResponseEntity.ok(response);
  }

  @Hidden
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<DocumentDownloadResponse> downloadDocument(
      ServiceName serviceName, UUID id, String documentId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  private Optional<ApplicationReadModel> findApplicationAwaitingProjection(UUID applicationId) {
    return projectionGateway.findProjection(
        new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class);
  }
}
