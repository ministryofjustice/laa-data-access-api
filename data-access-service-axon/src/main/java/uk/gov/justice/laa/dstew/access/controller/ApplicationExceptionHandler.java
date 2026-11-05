package uk.gov.justice.laa.dstew.access.controller;

import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.modelling.entity.EntityMissingForInstanceCommandHandlerException;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryIntegrityException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Translates command-side failures to the existing HTTP validation contract.
 *
 * <p>Logging rule (see {@code docs/observability-sentry-policy.md}): handlers that return a 4xx
 * response log at {@code WARN} (breadcrumb-only, no Sentry event) because the failure is
 * client-caused and expected; handlers that return a 5xx response log at {@code ERROR} (Sentry
 * event via the log route) because the failure is unexpected and operator-actionable. Each handler
 * logs explicitly here so severity does not depend on {@code LoggingAspects} classifying the
 * exception correctly upstream.
 */
@Slf4j
@RestControllerAdvice
public class ApplicationExceptionHandler {

  /** Returns a forbidden response when the authenticated identity has no usable Entra OID. */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException exception) {
    log.warn("Access denied: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage()));
  }

  /** Returns validation errors using the production service's Problem Detail shape. */
  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    log.warn("Validation failed: errors={}", exception.errors());
    return validationError(exception.errors());
  }

  /** Returns invalid shared query parameters using the standard bad-request response. */
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException exception) {
    log.warn("Invalid request argument: {}", exception.getMessage());
    return validationError(List.of(exception.getMessage()));
  }

  /** Returns field-level errors when a {@code @Valid} request body fails bean validation. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception) {
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                (FieldError error) ->
                    "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
            .toList();
    log.warn("Request body validation failed: errors={}", errors);
    return validationError(errors);
  }

  /** Returns a conflict when linked-application membership is incompatible with current state. */
  @ExceptionHandler(ApplicationLinkConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationLinkConflictException(
      ApplicationLinkConflictException exception) {
    log.warn("Application link conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a missing linked application as a standard HTTP not-found response. */
  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> handleResourceNotFoundException(
      ResourceNotFoundException exception) {
    log.warn("Resource not found: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  /** Returns a missing linked application as a standard HTTP not-found response. */
  @ExceptionHandler(EntityNotFoundException.class)
  ResponseEntity<ProblemDetail> handleResourceNotFoundException(EntityNotFoundException exception) {
    log.warn("Entity not found: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  /** Returns Axon's missing entity failure as a stable Application not-found response. */
  @ExceptionHandler(EntityMissingForInstanceCommandHandlerException.class)
  ResponseEntity<ProblemDetail> handleAggregateNotFoundException() {
    log.warn("Aggregate not found for command");
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested application was not found"));
  }

  /** Returns a conflict when an application ID is reused with different creation data. */
  @ExceptionHandler(ApplicationCreationConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationCreationConflictException(
      ApplicationCreationConflictException exception) {
    log.warn("Application creation conflict: applicationId={}", exception.getApplicationId());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Application ID "
                    + exception.getApplicationId()
                    + " already exists with different creation data"));
  }

  /** Returns a conflict when a prior-authority submission ID is reused on create. */
  @ExceptionHandler(PriorAuthorityCreationConflictException.class)
  ResponseEntity<ProblemDetail> handlePriorAuthorityCreationConflictException(
      PriorAuthorityCreationConflictException exception) {
    log.warn(
        "Prior authority creation conflict: priorAuthorityId={}", exception.getPriorAuthorityId());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Prior authority submission ID "
                    + exception.getPriorAuthorityId()
                    + " already exists"));
  }

  /** Returns a conflict when a prior-authority decision is incompatible with current status. */
  @ExceptionHandler(PriorAuthorityStatusConflictException.class)
  ResponseEntity<ProblemDetail> handlePriorAuthorityStatusConflictException(
      PriorAuthorityStatusConflictException exception) {
    log.warn("Prior authority status conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when a decision was based on a stale Prior Authority version. */
  @ExceptionHandler(PriorAuthorityVersionConflictException.class)
  ResponseEntity<ProblemDetail> handlePriorAuthorityVersionConflictException(
      PriorAuthorityVersionConflictException exception) {
    log.warn("Prior authority version conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when a decision was based on a stale Application version. */
  @ExceptionHandler(ApplicationVersionConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationVersionConflictException(
      ApplicationVersionConflictException exception) {
    log.warn("Application version conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when a linked-group change was based on a stale group version. */
  @ExceptionHandler(LinkedApplicationGroupVersionConflictException.class)
  ResponseEntity<ProblemDetail> handleLinkedApplicationGroupVersionConflictException(
      LinkedApplicationGroupVersionConflictException exception) {
    log.warn("Linked application group version conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when a work-item assignment is stale or incompatible with its state. */
  @ExceptionHandler(WorkItemAssignmentConflictException.class)
  ResponseEntity<ProblemDetail> handleWorkItemAssignmentConflictException(
      WorkItemAssignmentConflictException exception) {
    log.warn("Work item assignment conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when manual readiness would overwrite an automatic grant. */
  @ExceptionHandler(ApplicationAutoGrantOutcomeConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationAutoGrantOutcomeConflictException(
      ApplicationAutoGrantOutcomeConflictException exception) {
    log.warn("Application auto-grant outcome conflict: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns an unprocessable response when manual readiness is invalid for the lifecycle state. */
  @ExceptionHandler(InvalidApplicationStateException.class)
  ResponseEntity<ProblemDetail> handleInvalidApplicationStateException(
      InvalidApplicationStateException exception) {
    log.warn("Invalid application state transition: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage()));
  }

  /** Returns 409 when a file with the same name already exists in SDS. */
  @ExceptionHandler(FileConflictException.class)
  ResponseEntity<ProblemDetail> handleFileConflictException(FileConflictException exception) {
    log.warn("File conflict: {}", exception.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problemDetail.setTitle("Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
  }

  /** Returns 411 when file content length is missing from the SDS request. */
  @ExceptionHandler(FileLengthRequiredException.class)
  ResponseEntity<ProblemDetail> handleFileLengthRequiredException(
      FileLengthRequiredException exception) {
    log.warn("File length required: {}", exception.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.LENGTH_REQUIRED, exception.getMessage());
    problemDetail.setTitle("Length Required");
    return ResponseEntity.status(HttpStatus.LENGTH_REQUIRED).body(problemDetail);
  }

  /** Returns 400 when a virus is detected in an uploaded file by SDS. */
  @ExceptionHandler(VirusDetectedException.class)
  ResponseEntity<ProblemDetail> handleVirusDetectedException(VirusDetectedException exception) {
    log.warn("Virus detected in uploaded file: {}", exception.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle("Virus Detected");
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /** Returns 500 when SDS virus scan gives an unexpected result. */
  @ExceptionHandler(VirusScanException.class)
  ResponseEntity<ProblemDetail> handleVirusScanException(VirusScanException exception) {
    log.error("SDS virus scan gave an unexpected result", exception);
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    problemDetail.setTitle("Virus Scan Error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
  }

  /** Returns 500 when the SDS OAuth2 token cannot be obtained. */
  @ExceptionHandler(ClientAuthorizationException.class)
  ResponseEntity<ProblemDetail> handleClientAuthorizationException(
      ClientAuthorizationException exception) {
    log.error("Failed to obtain access token for an external service", exception);
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to obtain access token for an external service");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
  }

  /** Returns HTTP 500 with a stable, safe detail when application-history data is inconsistent. */
  @ExceptionHandler(ApplicationHistoryIntegrityException.class)
  ResponseEntity<ProblemDetail> handleApplicationHistoryIntegrityException(
      ApplicationHistoryIntegrityException exception) {
    log.error(
        "Application history integrity failure [applicationId={}, priorAuthorityId={}, reason={}]",
        exception.getApplicationId(),
        exception.getPriorAuthorityId(),
        exception.getReason());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Application history data is inconsistent");
    problemDetail.setInstance(URI.create("about:blank"));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
  }

  private ResponseEntity<ProblemDetail> validationError(List<String> errors) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Generic Validation Error");
    problemDetail.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problemDetail);
  }
}
