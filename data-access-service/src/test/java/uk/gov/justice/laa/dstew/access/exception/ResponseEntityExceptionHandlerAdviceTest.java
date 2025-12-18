package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;


class ResponseEntityExceptionHandlerAdviceTest {


  ResponseEntityExceptionHandlerAdvice handler = new ResponseEntityExceptionHandlerAdvice();

  public static Stream<Arguments> getDataTypes() {
    return Stream.of(
          Arguments.of(Integer.class, "Integer."),
          Arguments.of(String.class, "String."),
          Arguments.of(Double.class, "Double."),
          Arguments.of(Boolean.class, "Boolean."),
          Arguments.of(ApplicationStatus.class, Arrays.toString(ApplicationStatus.values()) + ".")
      );
  }

  @ParameterizedTest
  @MethodSource("getDataTypes")
  void handleHttpMessageNotReadable(Class<?> clazz, String expectedType) {

    HttpMessageNotReadableException exception2 = getHttpMessageNotReadableException(clazz);
    ResponseEntity<Object> objectResponseEntity = getResponseEntity(exception2);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals("Invalid data type for field 'testField'. Expected: %s".formatted(expectedType),
              problemDetail.getDetail());
  }

  @ParameterizedTest
  @MethodSource("getDataTypes")
  void handleTypeMismatch(Class<?> clazz, String expectedType) {

    PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(this, "testField", null, null);
    TypeMismatchException exception2 = new TypeMismatchException(propertyChangeEvent, clazz);

    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());
    ResponseEntity<Object> objectResponseEntity = handler.handleTypeMismatch(exception2,
            headers, status, request);
    assertThat(objectResponseEntity).isNotNull();
    assertInstanceOf(ProblemDetail.class, objectResponseEntity.getBody());
    ProblemDetail problemDetail = (ProblemDetail) objectResponseEntity.getBody();
    assertEquals(400, problemDetail.getStatus());
    assertEquals("Invalid data type for field 'testField'. Expected: %s".formatted(expectedType), problemDetail.getDetail());
  }

  private @Nullable ResponseEntity<Object> getResponseEntity(HttpMessageNotReadableException exception2) {
    HttpHeaders headers = new HttpHeaders();
    HttpStatus status = HttpStatus.BAD_REQUEST;
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());
    return handler.handleHttpMessageNotReadable(exception2,
            headers, status, request);
  }

  private static @NonNull HttpMessageNotReadableException getHttpMessageNotReadableException(Class<?> classExpected) {
    HttpMessageNotReadableException exception2 = Mockito.mock(HttpMessageNotReadableException.class);
    MismatchedInputException mismatchedInputException = Mockito.mock(MismatchedInputException.class);
    JsonMappingException.Reference reference = Mockito.mock(JsonMappingException.Reference.class);
    Mockito.when(reference.getFieldName()).thenReturn("testField");
    Mockito.when(mismatchedInputException.getPath()).thenReturn(List.of(reference));
    Class clazz = classExpected;
    Mockito.when(mismatchedInputException.getTargetType()).thenReturn(clazz);
    Mockito.when(exception2.getRootCause()).thenReturn(mismatchedInputException);
    return exception2;
  }



}