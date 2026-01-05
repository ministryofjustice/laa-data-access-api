package uk.gov.justice.laa.dstew.access.exception;


import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;




/**
 * Dedicated advice for framework /MVC exceptions that produce ResponseEntity/ProblemDetail.
 * Runs before other handlers so framework exceptions are handled here first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ResponseEntityExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

  private static @NonNull String getMessage(Object[] args, Class<?> requiredType) {

    requiredType = requiredType != null ? requiredType : Object.class;
    List<?> enumConstants = requiredType.isEnum() ? List.of(requiredType.getEnumConstants()) : List.of();
    String simpleName = requiredType.getSimpleName();
    return String.format("Invalid data type for field '%s'. Expected: %s.",
            args[0], enumConstants.isEmpty() ? simpleName : enumConstants);
  }

  /**
  * Handles validation errors for @Valid annotated request bodies.
  * Produces a ProblemDetail with all field errors listed.
  */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
          MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
          @NonNull HttpStatusCode status, @NonNull WebRequest request) {

    ProblemDetail problemDetail = ProblemDetailUtil.getCustomProblemDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
    problemDetail.setType(null);

    // Add field errors as additional properties
    Map<String, String> invalidFields = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
            invalidFields.put(error.getField(), error.getDefaultMessage())
    );

    problemDetail.setProperty("invalidFields", invalidFields);

    return ResponseEntity.badRequest().body(problemDetail);

  }
  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of String).
   * Produces a ProblemDetail with a helpful message when a field has incorrect type.
   */

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
    Throwable root = ex.getRootCause();
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
      Object[] args = {field};

      message = getMessage(args, mie.getTargetType());
      log.warn("Type mismatch for field {}: expected {}", field, expectedType, ex);
    } else {
      log.error("Failed to read request body", ex);
    }

    ProblemDetail problemDetail = ProblemDetailUtil.getCustomProblemDetail(
            HttpStatus.BAD_REQUEST, message);
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of String).
   * Produces a ProblemDetail with a helpful message when a field has incorrect type.
   */
  @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, @NonNull HttpHeaders headers,
                                                        @NonNull HttpStatusCode status, @NonNull WebRequest request) {

    Object[] args = {ex.getPropertyName(), ex.getValue()};
    String message = getMessage(args, ex.getRequiredType());
    String messageCode = ErrorResponse.getDefaultDetailMessageCode(TypeMismatchException.class, null);
    ProblemDetail body = createProblemDetail(ex, status, message, messageCode, args, request);
    body.setType(null);
    return handleExceptionInternal(ex, body, headers, status, request);
  }
}
