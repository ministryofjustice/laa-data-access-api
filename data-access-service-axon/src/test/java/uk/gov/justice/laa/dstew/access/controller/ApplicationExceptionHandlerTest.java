package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryIntegrityException;
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
  void givenInvalidPagination_whenHandled_thenReturnsValidationResponse() {
    var response =
        handler.handleIllegalArgumentException(
            new IllegalArgumentException("pageSize cannot be more than 100"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getProperties())
        .containsEntry("errors", List.of("pageSize cannot be more than 100"));
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
  void givenMissingAggregate_whenHandled_thenReturnsStableApplicationNotFoundResponse() {
    var response = handler.handleAggregateNotFoundException();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getDetail())
        .isEqualTo("The requested application was not found")
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

  @Test
  void givenIncompatibleAutoGrantOutcome_whenHandled_thenReturnsConflict() {
    UUID applicationId = UUID.randomUUID();

    var response =
        handler.handleApplicationAutoGrantOutcomeConflictException(
            new ApplicationAutoGrantOutcomeConflictException(applicationId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getDetail())
        .isEqualTo(
            "Application " + applicationId + " already has an incompatible auto-grant outcome");
  }

  @Test
  void givenInvalidLifecycleState_whenHandled_thenReturnsUnprocessableEntity() {
    UUID applicationId = UUID.randomUUID();

    var response =
        handler.handleInvalidApplicationStateException(
            new InvalidApplicationStateException(applicationId, "APPLICATION_IN_PROGRESS"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().getDetail())
        .isEqualTo(
            "Application "
                + applicationId
                + " cannot be made ready from status APPLICATION_IN_PROGRESS");
  }

  @Test
  void givenConflictingPriorAuthorityCreation_whenHandled_thenReturnsStablePublicConflictMessage() {
    UUID submissionId = UUID.randomUUID();

    var response =
        handler.handlePriorAuthorityCreationConflictException(
            new PriorAuthorityCreationConflictException(submissionId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getDetail())
        .isEqualTo(
            "Prior authority submission ID "
                + submissionId
                + " already exists with different creation data");
  }

  @Test
  void givenFileConflict_whenHandled_thenReturnsConflict() {
    var response =
        handler.handleFileConflictException(
            new FileConflictException("File already exists in SDS"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getDetail()).isEqualTo("File already exists in SDS");
  }

  @Test
  void givenFileLengthRequired_whenHandled_thenReturnsLengthRequired() {
    var response =
        handler.handleFileLengthRequiredException(
            new FileLengthRequiredException("File content length is required"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LENGTH_REQUIRED);
    assertThat(response.getBody().getDetail()).isEqualTo("File content length is required");
  }

  @Test
  void givenVirusDetected_whenHandled_thenReturnsBadRequest() {
    var response =
        handler.handleVirusDetectedException(
            new VirusDetectedException("Virus detected in uploaded file"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Virus detected in uploaded file");
  }

  @Test
  void givenVirusScanError_whenHandled_thenReturnsInternalServerError() {
    var response =
        handler.handleVirusScanException(
            new VirusScanException("Virus scan gave a non-standard result"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getDetail()).isEqualTo("Virus scan gave a non-standard result");
  }

  @Test
  void givenIntegrityFailure_whenHandled_thenReturnsHttp500WithStableProblemDetail() {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var integrityException =
        new ApplicationHistoryIntegrityException(
            applicationId,
            submissionId,
            "conflicting priorAuthorityType values: [EXPERT, COUNSEL]");

    var response = handler.handleApplicationHistoryIntegrityException(integrityException);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getDetail())
        .isEqualTo("Application history data is inconsistent");
    assertThat(response.getBody().getInstance()).isEqualTo(URI.create("about:blank"));
    assertThat(response.getBody().toString())
        .doesNotContain(applicationId.toString())
        .doesNotContain(submissionId.toString())
        .doesNotContain("conflicting");
  }
}
