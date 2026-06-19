package uk.gov.justice.laa.dstew.access.usecase.shared.parser;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import uk.gov.justice.laa.dstew.access.exception.JacksonExceptionMessageBuilder;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Utility service to convert arbitrary payloads into typed POJOs and validate them using Bean
 * Validation. Any mapping or validation errors are converted into {@link ValidationException} so
 * they are handled consistently by {@code GlobalExceptionHandler}.
 */
@RequiredArgsConstructor
public class PayloadValidator {

  private final ObjectMapper objectMapper;
  private final Validator validator;

  /**
   * Converts the given source object into an instance of the specified target type and validates
   * it. If mapping or validation fails, a {@link ValidationException}
   *
   * @param source to be converted and validated
   * @param targetType the desired target type
   * @param <T> the target type
   * @return an instance of the target type
   * @throws ValidationException if mapping or validation fails
   */
  public <T> T convertAndValidate(Object source, Class<T> targetType) {
    final T target;
    try {
      target = mapSource(source, targetType);
    } catch (IllegalArgumentException ex) {
      String message = "Invalid request payload";
      if (ex.getCause() instanceof MismatchedInputException mie) {
        message = JacksonExceptionMessageBuilder.buildMessageForInvalidEnum(ex, mie);
      } else if (ex.getCause() instanceof DatabindException jsonMappingException) {
        message = jsonMappingException.getOriginalMessage();
      }
      throw new ValidationException(List.of(message));
    } catch (JacksonException ex) {
      String message = JacksonExceptionMessageBuilder.buildMessage(ex);
      throw new ValidationException(List.of(message));
    }

    Set<ConstraintViolation<T>> violations = validator.validate(target);
    if (!violations.isEmpty()) {
      List<String> messages =
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .collect(Collectors.toList());
      throw new ValidationException(messages);
    }

    return target;
  }

  private <T> T mapSource(Object source, Class<T> targetType) throws JacksonException {
    if (source instanceof String json) {
      return objectMapper.readValue(json, targetType);
    }
    return objectMapper.convertValue(source, targetType);
  }
}
