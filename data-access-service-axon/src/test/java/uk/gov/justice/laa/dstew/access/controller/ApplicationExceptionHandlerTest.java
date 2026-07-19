package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplicationExceptionHandlerTest {

  private final ApplicationExceptionHandler handler = new ApplicationExceptionHandler();

  @Test
  void givenValidationFailure_whenHandled_thenReturnsPublicProblemDetailsAndErrors() {
    var response =
        handler.handleValidationException(
            new ValidationException(List.of("Exactly one application ID must be provided")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Generic Validation Error");
    assertThat(response.getBody().getProperties())
        .containsEntry("errors", List.of("Exactly one application ID must be provided"));
    assertThat(response.getBody().toString()).doesNotContain("Axon");
  }

  @Test
  void givenMissingResource_whenHandled_thenReturnsNotFoundWithoutImplementationDetails() {
    UUID applicationId = UUID.randomUUID();

    var response =
        handler.handleResourceNotFoundException(
            new ResourceNotFoundException("No application found with id: " + applicationId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getDetail())
        .isEqualTo("No application found with id: " + applicationId)
        .doesNotContain("Axon", "aggregate", "event store");
  }

  @Test
  void givenStaleVersion_whenHandled_thenReturnsConflict() {
    UUID applicationId = UUID.randomUUID();

    var response =
        handler.handleApplicationVersionConflictException(
            new ApplicationVersionConflictException(applicationId, 4L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getDetail())
        .isEqualTo("Application with id " + applicationId + " and version 4 not found");
  }

  @Test
  void givenConflictingCreation_whenHandled_thenReturnsStablePublicConflictMessage() {
    UUID applicationId = UUID.randomUUID();

    var response =
        handler.handleApplicationCreationConflictException(
            new ApplicationCreationConflictException(applicationId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getDetail())
        .isEqualTo(
            "Application ID " + applicationId + " already exists with different creation data");
  }

  @Test
  void givenGroupInvariantFailure_whenHandled_thenReturnsBadRequest() {
    var response =
        handler.handleApplicationGroupInvariantException(
            new ApplicationGroupInvariantException("Application cannot be its own lead"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Application cannot be its own lead");
  }
}
