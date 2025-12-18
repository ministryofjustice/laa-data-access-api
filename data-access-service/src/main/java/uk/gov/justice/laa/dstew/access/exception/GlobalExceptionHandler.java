package uk.gov.justice.laa.dstew.access.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;





/**
 * The global exception handler for all exceptions.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * The handler for ResourceNotFoundException.
   * Generic exception for when resource such as Application or Caseworker not found
   *
   * @param exception the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException exception) {
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, exception.getMessage());
    problemDetail.setTitle("Not found"); //Is this needed as ProblemDetail.forStatus already sets it?
    return ResponseEntity.status(NOT_FOUND).body(problemDetail);
  }


  /**
   * The handler for ViolationException.
   *
   * @param exception the exception.
   * @return the response with errors.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, exception.getMessage());
    problemDetail.setTitle(BAD_REQUEST.getReasonPhrase());
    problemDetail.setDetail("Generic Validation Error");
    problemDetail.setProperty("errors", exception.errors());
    return ResponseEntity.badRequest().body(problemDetail);
  }


  /**
   * The handler for ViolationException.
   *
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public void handleAuthorizationDeniedException(AuthorizationDeniedException exception) 
      throws AuthorizationDeniedException {
    throw exception; //rely on Spring ExceptionTranslationFilter to differ between 403 and 401 return codes
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
    log.error(logMessage, exception);
    // Do NOT use the exception type or message in the response (it may leak security-sensitive info)
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, logMessage);
    problemDetail.setTitle(INTERNAL_SERVER_ERROR.getReasonPhrase());
    return ResponseEntity.internalServerError().body(problemDetail);
  }

  /**The handler for Spring DataAccessExceptions.
   *
   * @param exception Data Access Exception
   * @return the response with the fixed title and detail
   */
  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException exception) {
    // Do NOT use the exception type or message in the response (it may leak security-sensitive info)
    final String responseMessage = "An unexpected error has occurred.";
    final String logMessage = "Database error has occurred type : %s".formatted(exception.getClass().getSimpleName());
    log.error(logMessage, exception);
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, responseMessage);
    problemDetail.setTitle(INTERNAL_SERVER_ERROR.getReasonPhrase());
    return ResponseEntity.internalServerError().body(problemDetail);
  }


}
