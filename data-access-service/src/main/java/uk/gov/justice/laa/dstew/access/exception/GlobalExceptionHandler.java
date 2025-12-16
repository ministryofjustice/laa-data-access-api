package uk.gov.justice.laa.dstew.access.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;


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
    final ProblemDetail problemDetail = ProblemDetail.forStatus(NOT_FOUND);
    problemDetail.setType(URI.create("error:not-found"));
    problemDetail.setTitle("Not found");
    problemDetail.setDetail(exception.getMessage());
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
    final var pd = ProblemDetail.forStatus(BAD_REQUEST);
    pd.setTitle("Validation failed");
    pd.setDetail(exception.getMessage());
    pd.setProperty("errors", exception.errors());
    return ResponseEntity.badRequest().body(pd);
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
    final var logMessage = "An unexpected application error has occurred.";
    log.error(logMessage, exception);
    // Do NOT use the exception type or message in the response (it may leak security-sensitive info)
    final var pd = ProblemDetail.forStatus(INTERNAL_SERVER_ERROR);
    pd.setTitle(INTERNAL_SERVER_ERROR.getReasonPhrase());
    pd.setDetail(logMessage);
    return ResponseEntity.internalServerError().body(pd);
  }

  /**The handler for Spring DataAccessExceptions.
   *
   * @param exception Data Access Exception
   * @return the response with the fixed title and detail
   */
  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException exception) {
    // Do NOT use the exception type or message in the response (it may leak security-sensitive info)
    final String responseMessage = "An unexpected application error has occurred.";
    final String logMessage = "Database error has occurred type : %s".formatted(exception.getClass().getSimpleName());
    log.error(logMessage, exception);
    final ProblemDetail problemDetail = ProblemDetail.forStatus(INTERNAL_SERVER_ERROR);
    problemDetail.setTitle(INTERNAL_SERVER_ERROR.getReasonPhrase());
    problemDetail.setDetail(responseMessage);
    return ResponseEntity.internalServerError().body(problemDetail);
  }

  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of String).
   * Produces a ProblemDetail with a helpful message when a field has incorrect type.
   */
  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class} )
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(Exception ex) {

    switch (ex){
      case MethodArgumentTypeMismatchException matme -> {
        String field = matme.getName();
        String expectedType = matme.getRequiredType() != null ? matme.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Invalid data type for field '%s'. Expected %s.", field, expectedType);
        log.warn("Type mismatch for field {}: expected {}", field, expectedType, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatus(BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setDetail(message);
        return ResponseEntity.badRequest().body(problemDetail);
      }

      case HttpMessageNotReadableException matme -> {
        Throwable root = matme.getRootCause();
        String message = "Invalid request payload";

        if (root instanceof MismatchedInputException mie) {
          String field = mie.getPath().stream()
                  .map(JsonMappingException.Reference::getFieldName)
                  .filter(Objects::nonNull)
                  .collect(Collectors.joining("."));

          if (field.isEmpty()) {
            field = "unknown";
          }

          String expectedType = mie.getTargetType() != null ? mie.getTargetType().getSimpleName() : "unknown";

          message = String.format("Invalid data type for field '%s'. Expected %s.", field, expectedType);
          log.warn("Type mismatch for field {}: expected {}", field, expectedType, ex);
        } else {
          log.error("Failed to read request body", ex);
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setDetail(message);
        return ResponseEntity.badRequest().body(problemDetail);
      }
        default -> throw new IllegalStateException("Unexpected value: " + ex);
    }

  }


}
