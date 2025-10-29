package uk.gov.justice.laa.dstew.access.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * The global exception handler for all exceptions.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /**
   * The handler for ApplicationNotFoundException.
   *
   * @param exception the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(ApplicationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleApplicationNotFound(ApplicationNotFoundException exception) {
    final var pd = ProblemDetail.forStatus(NOT_FOUND);
    pd.setTitle("Not found");
    pd.setDetail(exception.getMessage());
    return ResponseEntity.status(NOT_FOUND).body(pd);
  }

  /**
   * The handler for ViolationException.
   *
   * @param exception the exception.
   * @return the response with errors.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(ValidationException exception) {
    final var pd = ProblemDetail.forStatus(BAD_REQUEST);
    pd.setTitle("Validation failed");
    pd.setDetail(exception.getMessage());
    pd.setProperty("errors", exception.errors());
    return ResponseEntity.badRequest().body(pd);
  }

  /**
   * The handler for ViolationException.
   *
   * @param exception the exception.
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
    final var logMessage = "An unexpected application error has occurred.";
    log.error(logMessage, exception);
    // Do NOT use the exception type or message in the response (it may leak security-sensitive info)
    final var pd = ProblemDetail.forStatus(INTERNAL_SERVER_ERROR);
    pd.setTitle("Internal server error");
    pd.setDetail(logMessage);
    return ResponseEntity.internalServerError().body(pd);
  }
}
