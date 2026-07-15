package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Translates command-side failures to the existing HTTP validation contract. */
@RestControllerAdvice
public class ApplicationExceptionHandler {

  /** Returns validation errors using the production service's Problem Detail shape. */
  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    return validationError(exception.errors());
  }

  private ResponseEntity<ProblemDetail> validationError(List<String> errors) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Generic Validation Error");
    problemDetail.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problemDetail);
  }
}
