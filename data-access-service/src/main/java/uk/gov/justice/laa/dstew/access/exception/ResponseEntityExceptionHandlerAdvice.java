package uk.gov.justice.laa.dstew.access.exception;

import static uk.gov.justice.laa.dstew.access.exception.ProblemDetailUtility.getCustomProblemDetail;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Dedicated advice for framework /MVC exceptions that produce ResponseEntity/ProblemDetail. Runs
 * before other handlers so framework exceptions are handled here first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ResponseEntityExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

  /**
   * Handles validation errors for @Valid annotated request bodies. Produces a ProblemDetail with
   * all field errors listed.
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    ProblemDetail problemDetail =
        getCustomProblemDetail(HttpStatus.BAD_REQUEST, "Request validation failed");

    // Add field errors as additional properties
    Map<String, String> invalidFields = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> invalidFields.put(error.getField(), error.getDefaultMessage()));

    problemDetail.setProperty("invalidFields", invalidFields);

    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of
   * String). Produces a ProblemDetail with a helpful message when a field has incorrect type.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    Throwable root = ex.getRootCause();
    Throwable cause = ex.getCause();

    String message =
        switch (root) {
          case IllegalArgumentException iae
              when cause instanceof MismatchedInputException mie
                  && mie.getTargetType() != null
                  && mie.getTargetType().isEnum() ->
              JacksonExceptionMessageBuilder.buildMessageForInvalidEnum(iae, mie);
          case IllegalArgumentException iae -> iae.getLocalizedMessage();
          case MismatchedInputException mie -> JacksonExceptionMessageBuilder.buildMessage(mie);
          case null, default -> {
            log.error("Failed to read request body", ex);
            yield "Invalid request payload";
          }
        };

    ProblemDetail problemDetail = getCustomProblemDetail(HttpStatus.BAD_REQUEST, message);
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * Handles deserialization errors due to malformed types (like sending an Object instead of
   * String). Produces a ProblemDetail with a helpful message when a field has incorrect type.
   */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    Class<?> classToCheck = ex.getRequiredType();
    String message;
    if (classToCheck == null) {
      message =
          "Failed to convert '" + ex.getPropertyName() + "' with value: '" + ex.getValue() + "'";
    } else {
      String expectedType =
          !classToCheck.isEnum()
              ? classToCheck.getSimpleName()
              : java.util.List.of(classToCheck.getEnumConstants()).toString();
      message =
          "Invalid data type for field '%s'. Expected: %s."
              .formatted(ex.getPropertyName(), expectedType);
    }
    String messageCode =
        ErrorResponse.getDefaultDetailMessageCode(TypeMismatchException.class, null);
    Object[] args = {ex.getPropertyName(), ex.getValue()};
    ProblemDetail body = createProblemDetail(ex, status, message, messageCode, args, request);
    body.setType(null);
    return handleExceptionInternal(ex, body, headers, status, request);
  }
}
