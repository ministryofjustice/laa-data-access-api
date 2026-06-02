package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.beans.PropertyChangeEvent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.MismatchedInputException;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;

class ResponseEntityExceptionHandlerAdviceTest {

  ResponseEntityExceptionHandlerAdvice handler = new ResponseEntityExceptionHandlerAdvice();

  public static Stream<Arguments> getDataTypes() {
    return Stream.of(
        Arguments.of(Integer.class, "Integer."),
        Arguments.of(String.class, "String."),
        Arguments.of(Double.class, "Double."),
        Arguments.of(Boolean.class, "Boolean."),
        Arguments.of(null, "Object."),
        Arguments.of(ApplicationStatus.class, Arrays.toString(ApplicationStatus.values()) + "."));
  }

  public static Stream<Arguments> getDataTypesForTypeMismatch() {
    return Stream.of(
        Arguments.of(Integer.class, "Integer."),
        Arguments.of(String.class, "String."),
        Arguments.of(Double.class, "Double."),
        Arguments.of(Boolean.class, "Boolean."),
        Arguments.of(ApplicationStatus.class, Arrays.toString(ApplicationStatus.values()) + "."));
  }

  public static Stream<Arguments> getPathAndErrors() {
    return Stream.of(
        Arguments.of(
            ApplicationStatus.class,
            "PROGRESS. Valid values are: APPLICATION_IN_PROGRESS, APPLICATION_SUBMITTED"),
        Arguments.of(CategoryOfLaw.class, "PROGRESS. Valid values are: FAMILY"),
        Arguments.of(null, "PROGRESS"));
  }

  @ParameterizedTest
  @MethodSource("getDataTypes")
  void handleHttpMessageNotReadable(Class<?> clazz, String expectedType) {

    HttpMessageNotReadableException exception =
        getHttpMessageNotReadableExceptionMismatchedInput(clazz);
    ResponseEntity<Object> objectResponseEntity = getResponseEntity(exception);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals(
        "Invalid data type for field 'testField'. Expected: %s".formatted(expectedType),
        problemDetail.getDetail());
  }

  @ParameterizedTest
  @MethodSource("getDataTypes")
  void handleHttpMessageNotReadable_EmptyField(Class<?> clazz, String expectedType) {

    HttpMessageNotReadableException exception =
        getHttpMessageNotReadableExceptionMismatchedInput_EmptyField(clazz);
    ResponseEntity<Object> objectResponseEntity = getResponseEntity(exception);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals(
        "Invalid data type for field 'unknown'. Expected: %s".formatted(expectedType),
        problemDetail.getDetail());
  }

  @ParameterizedTest
  @MethodSource("getPathAndErrors")
  void handleHttpMessageNotReadable_IllegalArgument(
      @Nullable Class<?> enumClass, String expectedMessage) {
    HttpMessageNotReadableException exception =
        getHttpMessageNotReadableExceptionIllegalArgumentException(enumClass);
    ResponseEntity<Object> objectResponseEntity = getResponseEntity(exception);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals("Unexpected value : %s".formatted(expectedMessage), problemDetail.getDetail());
  }

  @Test
  void handleHttpMessageNotReadable_WithArrayIndex() {
    HttpMessageNotReadableException exception =
        getHttpMessageNotReadableExceptionWithArrayIndex(
            BigDecimal.class, "proceedings", 0, "substantiveCostLimitation");
    ResponseEntity<Object> objectResponseEntity = getResponseEntity(exception);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals(
        "Invalid data type for field 'proceedings[0].substantiveCostLimitation'. Expected: BigDecimal.",
        problemDetail.getDetail());
  }

  @ParameterizedTest
  @MethodSource("getDataTypesForTypeMismatch")
  void handleTypeMismatch(Class<?> clazz, String expectedType) {

    PropertyChangeEvent propertyChangeEvent =
        new PropertyChangeEvent(this, "testField", null, null);
    TypeMismatchException exception = new TypeMismatchException(propertyChangeEvent, clazz);

    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());
    ResponseEntity<Object> objectResponseEntity =
        handler.handleTypeMismatch(exception, headers, status, request);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals(
        "Invalid data type for field 'testField'. Expected: %s".formatted(expectedType),
        problemDetail.getDetail());
  }

  @Test
  void handleTypeMismatchNull() {

    PropertyChangeEvent propertyChangeEvent =
        new PropertyChangeEvent(this, "testField", null, null);
    TypeMismatchException exception = new TypeMismatchException(propertyChangeEvent, null);

    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());
    ResponseEntity<Object> objectResponseEntity =
        handler.handleTypeMismatch(exception, headers, status, request);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals("Failed to convert 'testField' with value: 'null'", problemDetail.getDetail());
  }

  @Test
  void handleMethodArgumentNotValid() {

    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    MethodArgumentNotValidException methodArgumentNotValidException =
        mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.hasErrors()).thenReturn(true);
    FieldError fieldError = new FieldError("testField", "testField", "testMessage");
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
    ResponseEntity<Object> responseEntity =
        handler.handleMethodArgumentNotValid(
            methodArgumentNotValidException, headers, status, request);

    assertThat(responseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, responseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) responseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals("Bad Request", problemDetail.getTitle());
    Object invalidFields = problemDetail.getProperties().get("invalidFields");
    assertNotNull(invalidFields);
    assertInstanceOf(java.util.Map.class, invalidFields);
    Map<String, String> invalidFieldsMap = (Map<String, String>) invalidFields;
    assertEquals(1, invalidFieldsMap.size());
    assertEquals("testMessage", invalidFieldsMap.get("testField"));
  }

  private @Nullable ResponseEntity<Object> getResponseEntity(
      HttpMessageNotReadableException notReadableException) {
    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());
    return handler.handleHttpMessageNotReadable(notReadableException, headers, status, request);
  }

  private static @NonNull HttpMessageNotReadableException
      getHttpMessageNotReadableExceptionMismatchedInput(Class<?> classExpected) {
    HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
    MismatchedInputException mismatchedInputException = mock(MismatchedInputException.class);
    DatabindException.Reference reference = mock(DatabindException.Reference.class);
    when(reference.getPropertyName()).thenReturn("testField");
    when(mismatchedInputException.getPath()).thenReturn(List.of(reference));
    Class clazz = classExpected;
    when(mismatchedInputException.getTargetType()).thenReturn(clazz);
    when(exception.getRootCause()).thenReturn(mismatchedInputException);
    return exception;
  }

  private static @NonNull HttpMessageNotReadableException
      getHttpMessageNotReadableExceptionMismatchedInput_EmptyField(Class<?> classExpected) {
    HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
    MismatchedInputException mismatchedInputException = mock(MismatchedInputException.class);
    DatabindException.Reference reference = mock(DatabindException.Reference.class);
    when(reference.getPropertyName()).thenReturn(null);
    when(reference.getIndex()).thenReturn(-1); // No index
    when(mismatchedInputException.getPath()).thenReturn(List.of(reference));
    Class clazz = classExpected;
    when(mismatchedInputException.getTargetType()).thenReturn(clazz);
    when(exception.getRootCause()).thenReturn(mismatchedInputException);
    return exception;
  }

  private static @NonNull HttpMessageNotReadableException
      getHttpMessageNotReadableExceptionIllegalArgumentException(@Nullable Class<?> enumClass) {
    HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
    IllegalArgumentException illegalArgumentException = mock(IllegalArgumentException.class);
    when(illegalArgumentException.getLocalizedMessage()).thenReturn("Unexpected value : PROGRESS");
    when(exception.getRootCause()).thenReturn(illegalArgumentException);
    if (enumClass != null) {
      MismatchedInputException mismatchedInputException = mock(MismatchedInputException.class);
      @SuppressWarnings("rawtypes")
      Class rawClass = enumClass;
      when(mismatchedInputException.getTargetType()).thenReturn(rawClass);
      when(exception.getCause()).thenReturn(mismatchedInputException);
    }
    return exception;
  }

  private static @NonNull HttpMessageNotReadableException
      getHttpMessageNotReadableExceptionWithArrayIndex(
          Class<?> classExpected, String arrayFieldName, int arrayIndex, String nestedFieldName) {
    HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
    MismatchedInputException mismatchedInputException = mock(MismatchedInputException.class);

    // Create reference for the array field (e.g., "proceedings")
    DatabindException.Reference arrayReference = mock(DatabindException.Reference.class);
    when(arrayReference.getPropertyName()).thenReturn(arrayFieldName);
    when(arrayReference.getIndex()).thenReturn(-1); // Not an array index itself

    // Create reference for the array index (e.g., [0])
    DatabindException.Reference indexReference = mock(DatabindException.Reference.class);
    when(indexReference.getPropertyName())
        .thenReturn(null); // Array indices don't have property names
    when(indexReference.getIndex()).thenReturn(arrayIndex);

    // Create reference for the nested field (e.g., "substantiveCostLimitation")
    DatabindException.Reference fieldReference = mock(DatabindException.Reference.class);
    when(fieldReference.getPropertyName()).thenReturn(nestedFieldName);
    when(fieldReference.getIndex()).thenReturn(-1); // Not an array index

    when(mismatchedInputException.getPath())
        .thenReturn(List.of(arrayReference, indexReference, fieldReference));
    Class clazz = classExpected;
    when(mismatchedInputException.getTargetType()).thenReturn(clazz);
    when(exception.getRootCause()).thenReturn(mismatchedInputException);
    return exception;
  }
}
