package uk.gov.justice.laa.dstew.access.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;


/**
 * The global exception handler for all exceptions.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * The handler for ApplicationNotFoundException.
   *
   * @param ex the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(ApplicationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleApplicationNotFound(ApplicationNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(NOT_FOUND);
    pd.setTitle("Not found");
    pd.setDetail(ex.getMessage());
    return ResponseEntity.status(NOT_FOUND).body(pd);
  }

  /**
   * The handler for CaseworkerNotFoundException.
   *
   * @param ex the exception.
   * @return the response with exception message.
   */
  @ExceptionHandler(CaseworkerNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleCaseworkerNotFound(CaseworkerNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(NOT_FOUND);
    pd.setTitle("Not found");
    pd.setDetail(ex.getMessage());
    return ResponseEntity.status(NOT_FOUND).body(pd);
  }

  /**
   * The handler for ValidationException.
   *
   * @param ex the exception.
   * @return the response with errors.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(BAD_REQUEST);
    pd.setTitle("Validation failed");
    pd.setDetail(ex.getMessage());
    pd.setProperty("errors", ex.errors());
    return ResponseEntity.badRequest().body(pd);
  }

  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of String).
   *
   * @param ex the exception.
   * @return the response with a descriptive error message.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedJson(HttpMessageNotReadableException ex) {
    Throwable root = ex.getRootCause();
    String message = "Invalid request payload";

    if (root instanceof MismatchedInputException mie) {
      String field = mie.getPath().stream()
          .map(JsonMappingException.Reference::getFieldName)
          .filter(f -> f != null)
          .reduce((a, b) -> a + "." + b)
          .orElse("unknown");

      String expectedType = mie.getTargetType() != null ? mie.getTargetType().getSimpleName() : "unknown";

      message = String.format("Invalid data type for field '%s'. Expected %s.", field, expectedType);
      log.warn("Type mismatch for field {}: expected {}", field, expectedType, ex);
    } else {
      log.error("Failed to read request body", ex);
    }

    ProblemDetail pd = ProblemDetail.forStatus(BAD_REQUEST);
    pd.setTitle("Bad Request");
    pd.setDetail(message);
    return ResponseEntity.badRequest().body(pd);
  }

  /**
   * The handler for AuthorizationDeniedException.
   *
   * @param exception the exception.
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public void handleAuthorizationDeniedException(AuthorizationDeniedException exception)
      throws AuthorizationDeniedException {
    throw exception;
  }
  /**
   * The handler for all other exceptions.
   *
   * @param ex the exception.
   * @return the response with fixed title and detail.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
    log.error("An unexpected application error has occurred.", ex);
    ProblemDetail pd = ProblemDetail.forStatus(INTERNAL_SERVER_ERROR);
    pd.setTitle("Internal server error");
    pd.setDetail("An unexpected application error has occurred.");
    return ResponseEntity.internalServerError().body(pd);
  }
}
