package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Proceeding;

class PayloadValidationServiceTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  PayloadValidationService serviceUnderTest =
      new PayloadValidationService(MapperUtil.getObjectMapper(), validator);

  public static Stream<Arguments> payloadsForValidation() {
    return Stream.of(Arguments.of(ApplicationContent.class), Arguments.of(Proceeding.class));
  }

  public static Stream<Arguments> invalidProceedingPayloads() {
    return Stream.of(
        Arguments.of(
            "Unexpected end-of-input within/between Object entries",
            """
            {"id": null,
            """),
        Arguments.of(
            "Invalid data type for field 'id'. Expected: UUID.",
            """
            {"id": 2 }
            """),
        Arguments.of(
            "Invalid data type for field 'substantiveCostLimitation'. Expected: Double.",
            """
            {"id": "550e8400-e29b-41d4-a716-446655440000",
            "leadProceeding": true,
            "description": "Test proceeding",
            "substantiveCostLimitation": "test"}
            """));
  }

  @Test
  public void validateProceedingPayloadFromJsonString() {

    final String json =
        """
        {"id": "",
        "leadProceeding": "",
        "description": "",
        "matterType": "",
        "categoryOfLaw": "",
        "usedDelegatedFunctions": ""}
        """;

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(json, Proceeding.class));

    assertThat(validationException.errors())
        .isInstanceOf(List.class)
        .contains("id: must not be null", "leadProceeding: must not be null");
  }

  @ParameterizedTest
  @MethodSource("invalidProceedingPayloads")
  public void illegalArgumentExceptionOnInvalidPayload_Proceedings(
      String expectedErrorMessage, String json) {

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(json, Proceeding.class));

    assertThat(validationException.errors())
        .isInstanceOf(List.class)
        .anyMatch(message -> message.contains(expectedErrorMessage));
  }

  @Test
  public void validateProceedingPayloadFromPojo() {

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors())
        .contains(
            "id: must not be null",
            "leadProceeding: must not be null",
            "description: must not be null");
  }

  @ParameterizedTest
  @MethodSource("payloadsForValidation")
  public <T> void validatePayloadsUsingSupplier(Class<T> clazz) {

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> serviceUnderTest.convertAndValidate(instantiate(clazz), clazz));

    assertThat(validationException.errors()).isNotEmpty();
  }

  private <T> T instantiate(Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException("Unable to instantiate " + clazz.getName(), e);
    }
  }

  @Test
  void
      convertAndValidate_whenMapperThrowsIllegalArgumentWithJsonMappingCause_returnsOriginalMessage() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    Validator validatorMock = mock(Validator.class);
    PayloadValidationService service = new PayloadValidationService(mapper, validatorMock);

    JsonParser jsonParser = mock(JsonParser.class);
    DatabindException mappingException = DatabindException.from(jsonParser, "bad payload");
    when(mapper.convertValue(any(), eq(Proceeding.class)))
        .thenThrow(new IllegalArgumentException("boom", mappingException));

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    String expectedMessage = mappingException.getOriginalMessage();
    assertThat(validationException.errors()).contains(expectedMessage);
    verify(validatorMock, never()).validate(any());
  }

  @Test
  void
      convertAndValidate_whenMapperThrowsIllegalArgumentWithNonEnumMismatchedInputCause_returnsIaeMessage() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    Validator validatorMock = mock(Validator.class);
    PayloadValidationService service = new PayloadValidationService(mapper, validatorMock);

    JsonParser jsonParser = mock(JsonParser.class);
    // String.class is not an enum — exercises the guard branch in buildMessageForInvalidEnum
    MismatchedInputException mie =
        MismatchedInputException.from(jsonParser, String.class, "type mismatch");
    IllegalArgumentException iae = new IllegalArgumentException("bad value", mie);
    when(mapper.convertValue(any(), eq(Proceeding.class))).thenThrow(iae);

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors()).contains(iae.getLocalizedMessage());
    verify(validatorMock, never()).validate(any());
  }

  @Test
  void
      convertAndValidate_whenMapperThrowsIllegalArgumentWithEnumMismatchedInputCause_returnsMessageWithValidEnumValues() {
    ObjectMapper mapper = mock(ObjectMapper.class);
    Validator validatorMock = mock(Validator.class);
    PayloadValidationService service = new PayloadValidationService(mapper, validatorMock);

    JsonParser jsonParser = mock(JsonParser.class);
    MismatchedInputException mie =
        MismatchedInputException.from(jsonParser, ApplicationStatus.class, "cannot deserialize");
    IllegalArgumentException iae =
        new IllegalArgumentException("Unexpected value 'INVALID_STATUS'", mie);
    when(mapper.convertValue(any(), eq(Proceeding.class))).thenThrow(iae);

    ValidationException validationException =
        Assertions.assertThrows(
            ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors())
        .anyMatch(
            m ->
                m.contains("Unexpected value 'INVALID_STATUS'")
                    && m.contains("APPLICATION_IN_PROGRESS")
                    && m.contains("APPLICATION_SUBMITTED"));
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
        Assertions.assertThrows(
            ValidationException.class,
            () -> service.convertAndValidate(new Proceeding(), Proceeding.class));

    assertThat(validationException.errors()).contains("Invalid request payload");
    verify(validatorMock, never()).validate(any());
  }
}
