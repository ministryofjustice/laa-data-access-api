package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class GlobalExceptionHandlerTest {

  GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

  @Test
  void handleApplicationNotFound_returnsGenericNotFoundExceptionAndErrorMessage() {
    var result = globalExceptionHandler.handleResourceNotFound(new ResourceNotFoundException("Application not found"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getDetail()).isEqualTo("Application not found");
  }

  @Test
  void handleCaseworkerNotFound_returnsGenericNotFoundExceptionAndErrorMessage() {
    var result = globalExceptionHandler.handleResourceNotFound(new ResourceNotFoundException("Caseworker not found"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getDetail()).isEqualTo("Caseworker not found");
  }

  @Test
  void handleValidationException_returnsBadRequestStatusAndErrors() {
    var result = globalExceptionHandler.handleValidationException(new ValidationException(List.of("error1")));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getProperties()).containsEntry("errors", List.of("error1"));
  }

  @Test
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    var result = globalExceptionHandler.handleGenericException(new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getDetail()).isEqualTo("An unexpected application error has occurred.");
  }

  @Test void handleAuthorizationDeniedException_throwsException_for_ExceptionTranslationFilter_to_handle() {
    assertThatException()
      .isThrownBy(() -> globalExceptionHandler.handleAuthorizationDeniedException(new AuthorizationDeniedException("")))
      .isInstanceOf(AuthorizationDeniedException.class);
  }

  @Test
  void handleDataAccessException_returnsInternalServerErrorStatusAndErrorMessage() {
    var result = globalExceptionHandler.handleDataAccessException(new DataRetrievalFailureException("Database error") {
    });

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getDetail()).isEqualTo("An unexpected application error has occurred.");
  }


  }
