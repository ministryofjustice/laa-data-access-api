package uk.gov.justice.laa.dstew.access.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.LENGTH_REQUIRED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static uk.gov.justice.laa.dstew.access.exception.ProblemDetailUtility.getCustomProblemDetail;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** The global exception handler for all exceptions with structured logging. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * The handler for ResourceNotFoundException. Generic exception for when resource such as
   * Application or Caseworker not found
   *
   * @param exception the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException exception) {
    log.warn("Resource not found: message={}", exception.getMessage());
    return ResponseEntity.status(NOT_FOUND)
        .body(getCustomProblemDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  /**
   * The handler for ViolationException.
   *
   * @param exception the exception.
   * @return the response with errors.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    log.warn("Validation error: errors={}", exception.errors());
    final ProblemDetail problemDetail =
        getCustomProblemDetail(HttpStatus.BAD_REQUEST, "Generic Validation Error");
    problemDetail.setProperty("errors", exception.errors());
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * The handler for IllegalArgumentException. Used for parameter validation (e.g., invalid page,
   * pageSize).
   *
   * @param exception the exception.
   * @return the response with the exception message.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
      IllegalArgumentException exception) {
    log.warn("Invalid argument: message={}", exception.getMessage());
    return ResponseEntity.badRequest()
        .body(getCustomProblemDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
  }

  /**
   * The handler for AuthorizationDeniedException.
   *
   * @param exception the exception.
   * @throws AuthorizationDeniedException re-thrown to let Spring Security handle response
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public void handleAuthorizationDeniedException(AuthorizationDeniedException exception)
      throws AuthorizationDeniedException {
    log.warn("Authorization denied: message={}", exception.getMessage());
    throw exception;
  }

  /**
   * The handler for OptimisticLockingFailureException.
   *
   * @param exception the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLoggingException(
      OptimisticLockingFailureException exception) {
    log.error("Optimistic locking failure: message={}", exception.getMessage(), exception);
    Sentry.captureException(exception);
    return ResponseEntity.status(CONFLICT)
        .body(getCustomProblemDetail(HttpStatus.CONFLICT, exception.getMessage()));
  }

  /**
   * The handler for AccessDeniedException.
   *
   * @param ex the exception.
   * @param req the HTTP request.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public void handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
    log.warn("Access denied: method={}, uri={}", req.getMethod(), req.getRequestURI());
    throw ex;
  }

  /**
   * The handler for Exception.
   *
   * @param exception the exception.
   * @return the response with fixed title and detail.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception exception) {
    final var logMessage = "An unexpected error has occurred.";
    log.error(
        "Unexpected error: exceptionType={}, message={}",
        exception.getClass().getSimpleName(),
        exception.getMessage(),
        exception);
    // Do NOT use the exception type or message in the response (it may leak security-sensitive
    // info)
    return ResponseEntity.internalServerError()
        .body(getCustomProblemDetail(INTERNAL_SERVER_ERROR, logMessage));
  }

  /**
   * The handler for Spring DataAccessExceptions.
   *
   * @param exception Data Access Exception
   * @return the response with the fixed title and detail
   */
  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException exception) {
    // Do NOT use the exception type or message in the response (it may leak security-sensitive
    // info)
    final String responseMessage = "An unexpected error has occurred.";
    final String exceptionType = exception.getClass().getSimpleName();
    log.error("Database error: exceptionType={}", exceptionType, exception);
    Sentry.captureException(exception);
    return ResponseEntity.internalServerError()
        .body(getCustomProblemDetail(INTERNAL_SERVER_ERROR, responseMessage));
  }

  /**
   * The handler for FileConflictException.
   *
   * @param ex the exception.
   * @return the response with the exception message.
   */
  @ExceptionHandler(FileConflictException.class)
  public ResponseEntity<ProblemDetail> handleFileConflictException(FileConflictException ex) {
    log.warn("File conflict: message={}", ex.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problemDetail.setTitle("Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
  }

  /**
   * The handler for FileLengthRequiredException.
   *
   * @param ex the exception.
   * @return the response with the exception message.
   */
  @ExceptionHandler(FileLengthRequiredException.class)
  public ResponseEntity<ProblemDetail> handleFileLengthRequiredException(
      FileLengthRequiredException ex) {
    log.warn("File length required: message={}", ex.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(LENGTH_REQUIRED, ex.getMessage());
    problemDetail.setTitle("Length Required");
    return ResponseEntity.status(LENGTH_REQUIRED).body(problemDetail);
  }

  /**
   * The handler for VirusDetectedException.
   *
   * @param ex the exception.
   * @return the response with the exception message.
   */
  @ExceptionHandler(VirusDetectedException.class)
  public ResponseEntity<ProblemDetail> handleVirusDetectedException(VirusDetectedException ex) {
    log.warn("Virus detected: message={}", ex.getMessage());
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Virus Detected");
    return ResponseEntity.status(BAD_REQUEST).body(problemDetail);
  }

  /**
   * The handler for VirusScanException.
   *
   * @param ex the exception.
   * @return the response with the exception message.
   */
  @ExceptionHandler(VirusScanException.class)
  public ResponseEntity<ProblemDetail> handleVirusScanException(VirusScanException ex) {
    log.error("Virus scan error: message={}", ex.getMessage(), ex);
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, ex.getMessage());
    problemDetail.setTitle("Virus Scan Error");
    return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(problemDetail);
  }
}
