package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class GlobalExceptionHandlerTest {

  GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

  @Test
  void handleApplicationNotFound_returnsNotFoundStatusAndErrorMessage() {
    var result = globalExceptionHandler.handleApplicationNotFound(new ApplicationNotFoundException("Application not found"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().getDetail()).isEqualTo("Application not found");
  }

  @Test
  void handleCaseworkerNotFound_returnsNotFoundStatusAndErrorMessage() {
    var result = globalExceptionHandler.handleCaseworkerNotFound(new CaseworkerNotFoundException("Caseworker not found"));

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
}
