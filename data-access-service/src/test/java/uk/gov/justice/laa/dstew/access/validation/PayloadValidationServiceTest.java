package uk.gov.justice.laa.dstew.access.validation;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.Proceeding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayloadValidationServiceTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  PayloadValidationService serviceUnderTest =
      new PayloadValidationService(MapperUtil.getObjectMapper(), validator);

  public static Stream<Arguments> payloadsForValidation() {
    return Stream.of(
        Arguments.of(ApplicationContent.class),
        Arguments.of(Proceeding.class)
    );
  }

  @Test
  public void validateProceedingPayloadFromJsonString() {

    final String json = """
        {"id": "",
        "leadProceeding": "",
        "description": "",
        "matterType": "",
        "categoryOfLaw": "",
        "usedDelegatedFunctions": ""}
        """;

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(json, Proceeding.class));

    assertThat(validationException.errors())
        .isInstanceOf(List.class)
        .contains("id: must not be null",
            "leadProceeding: must not be null");

  }

  @Test
  public void illegalArgumentExceptionOnInvalidPayload() {

    final String json = """
        {"id": null,
        """;

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(json, Proceeding.class));

    assertThat(validationException.errors())
        .isInstanceOf(List.class)
        .contains("Unexpected end-of-input within/between Object entries");

  }

  @Test
  public void illegalArgumentExceptionOnInvalidPayload2() {

    final String json = """
        {"id": 2 }
        """;

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(json, Proceeding.class));

    assertThat(validationException.errors())
        .isInstanceOf(List.class)
        .anyMatch(message -> message.contains("Cannot deserialize value of type `java.util.UUID`"));

  }

  @Test
  public void validateProceedingPayloadFromPojo() {

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors())
        .contains("id: must not be null",
            "leadProceeding: must not be null",
            "description: must not be null");

  }


  @ParameterizedTest
  @MethodSource("payloadsForValidation")
  public <T> void validatePayloadsUsingSupplier(Class<T> clazz) {

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(instantiate(clazz), clazz));

    assertThat(validationException.errors()).isNotEmpty();

  }

  private <T> T instantiate(Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IllegalStateException("Unable to instantiate " + clazz.getName(), e);
    }
  }

  @Test
  void convertAndValidate_whenMapperThrowsIllegalArgumentWithJsonMappingCause_returnsOriginalMessage() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    Validator validatorMock = mock(Validator.class);
    PayloadValidationService service = new PayloadValidationService(mapper, validatorMock);

    JsonMappingException mappingException = JsonMappingException.fromUnexpectedIOE(new IOException("bad data"));
    when(mapper.convertValue(any(), eq(Proceeding.class)))
        .thenThrow(new IllegalArgumentException("boom", mappingException));

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    String expectedMessage = mappingException.getOriginalMessage();
    assertThat(validationException.errors()).contains(expectedMessage);
    verify(validatorMock, never()).validate(any());
  }

  @Test
  void convertAndValidate_whenMapperThrowsIllegalArgumentWithoutCause_returnsGenericMessage() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    Validator validatorMock = mock(Validator.class);
    PayloadValidationService service = new PayloadValidationService(mapper, validatorMock);

    when(mapper.convertValue(any(), eq(Proceeding.class)))
        .thenThrow(new IllegalArgumentException("boom"));

    ValidationException validationException =
        Assertions.assertThrows(ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors()).contains("Invalid request payload");
    verify(validatorMock, never()).validate(any());
  }
}