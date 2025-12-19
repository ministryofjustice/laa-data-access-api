package uk.gov.justice.laa.dstew.access.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
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

  private static @NonNull String getMessage(Object[] args, Class<?> requiredType1) {

    Class<?> requiredType = requiredType1;
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

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );

        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.example.com/errors/validation-failed"));

        // Add field errors as additional properties
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        problemDetail.setProperty("fieldErrors", fieldErrors);

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

    ProblemDetail problemDetail = ProblemDetail.forStatus(BAD_REQUEST);
    problemDetail.setTitle("Bad Request");
    problemDetail.setDetail(message);
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

    return handleExceptionInternal(ex, body, headers, status, request);
  }

//  @Override
//  protected ResponseEntity<Object> handleMethodArgumentNotValid(
//          MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
//      List<String> errors = ex.getBindingResult().getAllErrors().stream().map(ResponseEntityExceptionHandlerAdvice::getErrorMessage)
//              .toList();
//
//      ProblemDetail problemDetail = ProblemDetail.forStatus(BAD_REQUEST);
//      problemDetail.setTitle("Bad Request");
//      problemDetail.setDetail("Binding errors");
//      IntStream.range(0, errors.size()).forEach(index -> problemDetail.setProperty(String.valueOf(index), errors.get(index)));
//      return ResponseEntity.badRequest().body(problemDetail);
//  }
//
//  private static String getErrorMessage(ObjectError error) {
//    return switch (error) {
//      case FieldError fieldError -> String.format("Field '%s': %s", fieldError.getField(), fieldError.getDefaultMessage());
//      default -> String.format("Object '%s': %s", error.getObjectName(), error.getDefaultMessage());
//    };
//  }
}
