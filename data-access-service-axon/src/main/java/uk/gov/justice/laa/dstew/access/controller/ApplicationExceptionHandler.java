package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Translates command-side failures to the existing HTTP validation contract. */
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

  /** Returns Axon's missing aggregate failure as a stable Application not-found response. */
  @ExceptionHandler(AggregateNotFoundException.class)
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

  /** Returns a conflict when a decision was based on a stale Application version. */
  @ExceptionHandler(ApplicationVersionConflictException.class)
  ResponseEntity<ProblemDetail> handleApplicationVersionConflictException(
      ApplicationVersionConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  private ResponseEntity<ProblemDetail> validationError(List<String> errors) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Generic Validation Error");
    problemDetail.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problemDetail);
  }
}
