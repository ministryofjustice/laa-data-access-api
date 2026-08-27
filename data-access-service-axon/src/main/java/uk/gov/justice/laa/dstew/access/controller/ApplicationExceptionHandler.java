package uk.gov.justice.laa.dstew.access.controller;

import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.modelling.entity.EntityMissingForInstanceCommandHandlerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryIntegrityException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Translates command-side failures to the existing HTTP validation contract. */
@Slf4j
@RestControllerAdvice
public class ApplicationExceptionHandler {

  /** Returns validation errors using the production service's Problem Detail shape. */
  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    return validationError(exception.errors());
  }

  /** Returns invalid shared query parameters using the standard bad-request response. */
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException exception) {
    return validationError(List.of(exception.getMessage()));
  }

  /** Returns a 400 when a command would violate an application-group invariant. */
  @ExceptionHandler(ApplicationGroupInvariantException.class)
  ResponseEntity<ProblemDetail> handleApplicationGroupInvariantException(
      ApplicationGroupInvariantException exception) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
  }

  /** Returns a missing linked application as a standard HTTP not-found response. */
  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> handleResourceNotFoundException(
      ResourceNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  /** Returns Axon's missing entity failure as a stable Application not-found response. */
  @ExceptionHandler(EntityMissingForInstanceCommandHandlerException.class)
  ResponseEntity<ProblemDetail> handleAggregateNotFoundException() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The requested application was not found"));
  }

  /** Returns a conflict when an application ID is reused with different creation data. */
  @ExceptionHandler(ApplicationCreationConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationCreationConflictException(
      ApplicationCreationConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Application ID "
                    + exception.getApplicationId()
                    + " already exists with different creation data"));
  }

  /**
   * Returns a conflict when a prior-authority submission ID is reused with different creation data.
   */
  @ExceptionHandler(PriorAuthorityCreationConflictException.class)
  ResponseEntity<ProblemDetail> handlePriorAuthorityCreationConflictException(
      PriorAuthorityCreationConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Prior authority submission ID "
                    + exception.getSubmissionId()
                    + " already exists with different creation data"));
  }

  /** Returns a conflict when a decision was based on a stale Application version. */
  @ExceptionHandler(ApplicationVersionConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationVersionConflictException(
      ApplicationVersionConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns a conflict when manual readiness would overwrite an automatic grant. */
  @ExceptionHandler(ApplicationAutoGrantOutcomeConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationAutoGrantOutcomeConflictException(
      ApplicationAutoGrantOutcomeConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /** Returns an unprocessable response when manual readiness is invalid for the lifecycle state. */
  @ExceptionHandler(InvalidApplicationStateException.class)
  ResponseEntity<ProblemDetail> handleInvalidApplicationStateException(
      InvalidApplicationStateException exception) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage()));
  }

  /** Returns HTTP 500 with a stable, safe detail when application-history data is inconsistent. */
  @ExceptionHandler(ApplicationHistoryIntegrityException.class)
  ResponseEntity<ProblemDetail> handleApplicationHistoryIntegrityException(
      ApplicationHistoryIntegrityException exception) {
    log.error(
        "Application history integrity failure [applicationId={}, submissionId={}, reason={}]",
        exception.getApplicationId(),
        exception.getSubmissionId(),
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
