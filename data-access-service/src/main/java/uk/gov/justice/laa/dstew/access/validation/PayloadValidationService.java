package uk.gov.justice.laa.dstew.access.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Utility service to convert arbitrary payloads into typed POJOs and
 * validate them using Bean Validation. Any mapping or validation errors are
 * converted into {@link ValidationException} so they are handled consistently
 * by {@code GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class PayloadValidationService {

  private final ObjectMapper objectMapper;
  private final Validator validator;


  /**
   * Converts the given source object into an instance of the specified target type
   * and validates it. If mapping or validation fails, a {@link ValidationException}
   *
   * @param source     to be converted and validated
   * @param targetType the desired target type
   * @param <T>        the target type
   * @return an instance of the target type
   * @throws ValidationException if mapping or validation fails
   */
  public <T> T convertAndValidate(Object source, Class<T> targetType) {
    final T target;
    try {
      target = mapSource(source, targetType);
    } catch (IllegalArgumentException ex) {
      String message = "Invalid request payload";
      if (ex.getCause() instanceof JsonMappingException jsonMappingException) {
        message = jsonMappingException.getOriginalMessage();
      }
      throw new ValidationException(List.of(message));
    } catch (JsonProcessingException ex) {
      throw new ValidationException(List.of(ex.getOriginalMessage()));
    }

    Set<ConstraintViolation<T>> violations = validator.validate(target);
    if (!violations.isEmpty()) {
      List<String> messages = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.toList());
      throw new ValidationException(messages);
    }

    return target;
  }

  private <T> T mapSource(Object source, Class<T> targetType) throws JsonProcessingException {
    if (source instanceof String json) {
      return objectMapper.readValue(json, targetType);
    }
    return objectMapper.convertValue(source, targetType);
  }
}
