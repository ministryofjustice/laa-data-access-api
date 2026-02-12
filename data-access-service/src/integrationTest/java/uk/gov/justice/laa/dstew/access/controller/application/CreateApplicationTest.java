package uk.gov.justice.laa.dstew.access.controller.application;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertCreated;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertProblemRecord;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationContentFactory;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateApplicationTest extends BaseIntegrationTest {
  private static final int applicationVersion = 1;

  private Stream<Arguments> createApplicationTestParameters() {
      return Stream.of(
              Arguments.of(new ApplicationOffice()),
              Arguments.of(ApplicationOffice.builder().code("XX456F").build())
      );
  }

  @ParameterizedTest
  @MethodSource("createApplicationTestParameters")
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader(
          ApplicationOffice office
  ) throws Exception {
      verifyCreateNewApplication(office, null);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenCreateNewApplication_whenCreateApplicationAndNoOffice_thenReturnCreatedWithLocationHeader() throws Exception {
    verifyCreateNewApplication(null, null);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_thenReturnCreatedWithLocationHeader() throws Exception {
    final ApplicationEntity leadApplicationToLink = persistedApplicationFactory.createAndPersist();
    final LinkedApplication linkedApplication = LinkedApplication.builder().leadApplicationId(leadApplicationToLink.getApplyApplicationId())
                                                            .associatedApplicationId(UUID.randomUUID())
                                                            .build();
    final var createdEntity = verifyCreateNewApplication(null, linkedApplication);
    final ApplicationEntity leadApplication = applicationRepository.findById(leadApplicationToLink.getId()).orElseThrow();
    assertLinkedApplicationCorrectlyApplied(leadApplication, createdEntity);
  }

  private ApplicationEntity verifyCreateNewApplication(ApplicationOffice office, LinkedApplication linkedApplication) throws Exception {
    ApplicationContentFactory applicationContentFactory = new ApplicationContentFactory();
    ApplicationContent content = applicationContentFactory.create();
    content.setOffice(office);
    content.setAllLinkedApplications(linkedApplication == null ? null : List.of(linkedApplication));

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create();
    applicationCreateRequest.setApplicationContent(objectMapper.convertValue(content, Map.class));

    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);

    assertSecurityHeaders(result);
    assertCreated(result);

    UUID createdApplicationId = HeaderUtils.GetUUIDFromLocation(result.getResponse().getHeader("Location"));
    ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId)
      .orElseThrow(() -> new ResourceNotFoundException(createdApplicationId.toString()));
    assertApplicationEqual(applicationCreateRequest, createdApplication);
    assertNotNull(createdApplicationId);

    domainEventAsserts.assertDomainEventForApplication(createdApplication, DomainEventType.APPLICATION_CREATED);
    return createdApplication;
  }

  @ParameterizedTest
  @MethodSource("applicationCreateRequestInvalidDataCases")
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenInvalidApplicationRequestData_whenCreateApplication_thenReturnBadRequest(ApplicationCreateRequest request,
                                                                                            ProblemDetail expectedDetail,
                                                                                            Map<String, Object> problemDetailProperties)
      throws Exception {
    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
    ProblemDetail detail = deserialise(result, ProblemDetail.class);

    // then
    expectedDetail.setProperties(problemDetailProperties);
    assertSecurityHeaders(result);
    assertProblemRecord(HttpStatus.BAD_REQUEST, expectedDetail, result, detail);
    assertEquals(0, applicationRepository.count());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenInvalidApplicationContent_EmptyMap_whenCreateApplication_thenReturnBadRequest() throws Exception {
    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create(builder -> {
      builder.applicationContent(new HashMap<>());
    });


    ProblemDetail expectedProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    expectedProblemDetail.setProperty("invalidFields", Map.of("applicationContent","size must be between 1 and 2147483647"));

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
    ProblemDetail validationException = deserialise(result, ProblemDetail.class);

    // then
    assertSecurityHeaders(result);
    assertProblemRecord(HttpStatus.BAD_REQUEST, expectedProblemDetail, result, validationException);
    assertEquals(0, applicationRepository.count());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenInvalidApplicationContent_whenCreateApplication_thenReturnBadRequest() throws Exception {
    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create(builder -> {
      builder.applicationContent(null);
    });

    Map<String, String> invalidFields = new HashMap<>();
    invalidFields.put("applicationContent", "size must be between 1 and 2147483647");

    ProblemDetail expectedProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    expectedProblemDetail.setProperty("invalidFields", invalidFields);
    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
    ProblemDetail validationException = deserialise(result, ProblemDetail.class);

    // then
    assertSecurityHeaders(result);
    assertProblemRecord(HttpStatus.BAD_REQUEST, expectedProblemDetail, result, validationException);
    assertEquals(0, applicationRepository.count());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{}"})
  @WithMockUser(authorities = TestConstants.Roles.WRITER)
  public void givenNoRequestBody_whenCreateApplication_thenReturnBadRequest(String request) throws Exception {
    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
    ProblemDetail detail = deserialise(result, ProblemDetail.class);

    // then
    assertSecurityHeaders(result);
    assertProblemRecord(HttpStatus.BAD_REQUEST, "Bad Request",
        "Invalid data type for field 'unknown'. Expected: ApplicationCreateRequest.", result, detail, null);
    assertEquals(0, applicationRepository.count());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden() throws Exception {
    // given
    ApplicationCreateRequest request = applicationCreateRequestFactory.create();

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised() throws Exception {
    // given
    ApplicationCreateRequest request = applicationCreateRequestFactory.create();

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

    // then
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  private Stream<Arguments> applicationCreateRequestInvalidDataCases() {
    ProblemDetail problemDetail =
        ProblemDetailBuilder.create().status(HttpStatus.BAD_REQUEST).title("Bad Request").detail("Request validation failed")
            .build();
    problemDetail.setType(null);
    String minimumSizErrorMessage = "size must be between 1 and " + Integer.MAX_VALUE;
    String mustNotBeNull = "must not be null";


    return Stream.of(
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.status(null);
        }), problemDetail, Map.of("invalidFields", Map.of("status", mustNotBeNull))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.laaReference(null);
        }), problemDetail, Map.of("invalidFields", Map.of("laaReference", mustNotBeNull))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.applicationContent(null);
        }), problemDetail, Map.of("invalidFields", Map.of("applicationContent", minimumSizErrorMessage))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.applicationContent(new HashMap<>());
        }), problemDetail, Map.of("invalidFields", Map.of("applicationContent", minimumSizErrorMessage))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.applicationContent(Map.of("applicationContent", Map.of("proceedings", List.of())));
        }), ProblemDetailBuilder.create().status(HttpStatus.BAD_REQUEST).title("Bad Request").detail("Generic Validation Error")
            .build(), Map.of("errors", List.of("id: must not be null",
            "submittedAt: must not be null"))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.individuals(null);
        }), problemDetail, Map.of("invalidFields", Map.of("individuals", "size must be between 1 and 2147483647"))),
        Arguments.of(applicationCreateRequestFactory.create(builder -> {
          builder.individuals(List.of());
        }), problemDetail, Map.of("invalidFields", Map.of("individuals", minimumSizErrorMessage))), Arguments.of(
            applicationCreateRequestFactory.create(builder -> builder.individuals(
                List.of(individualFactory.create(individualBuilder -> individualBuilder.dateOfBirth(null))))), problemDetail,
            Map.of("invalidFields", Map.of("individuals[0].dateOfBirth", mustNotBeNull))),
        Arguments.of(
            applicationCreateRequestFactory.create(builder -> builder.individuals(
                List.of(individualFactory.create(individualBuilder -> individualBuilder.details(null))))), problemDetail,
            Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
        Arguments.of(
            applicationCreateRequestFactory.create(builder -> builder.individuals(
                List.of(individualFactory.create(individualBuilder -> individualBuilder.details(new HashMap<>()))))),
            problemDetail, Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
        Arguments.of(
            applicationCreateRequestFactory.create(builder -> builder.individuals(List.of(individualFactory.create(
                individualBuilder -> individualBuilder.dateOfBirth(null).firstName("").lastName("").type(null)
                    .details(new HashMap<>()))))), problemDetail, Map.of("invalidFields",
                Map.of(
                    "individuals[0].details", minimumSizErrorMessage,
                    "individuals[0].type", mustNotBeNull,
                    "individuals[0].dateOfBirth", mustNotBeNull))),
        Arguments.of(applicationCreateRequestFactory.create(
                builder -> builder.individuals(List.of(individualFactory.create(
                    individualBuilder -> individualBuilder.dateOfBirth(null).firstName(null).lastName(null).details(null))))),
            problemDetail, Map.of("invalidFields",
                Map.of("individuals[0].details", minimumSizErrorMessage,
                    "individuals[0].lastName", mustNotBeNull,
                    "individuals[0].firstName", mustNotBeNull,
                    "individuals[0].dateOfBirth", mustNotBeNull
                )
            )));
  }


  private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual)
      throws JsonProcessingException {
    assertNotNull(actual.getId());

    JsonNode expectedContentNode = objectMapper.readTree(objectMapper.writeValueAsString(expected.getApplicationContent()));
    JsonNode actualContentNode = objectMapper.readTree(objectMapper.writeValueAsString(actual.getApplicationContent()));

    assertEquals(expectedContentNode, actualContentNode);

    assertEquals(expected.getLaaReference(), actual.getLaaReference());
    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(applicationVersion, actual.getSchemaVersion());
    assertNull(actual.getIsAutoGranted());
    assertNotNull(actual.getSubmittedAt());
  }

  private void assertLinkedApplicationCorrectlyApplied(ApplicationEntity leadApplication, ApplicationEntity linkedApplication) {
    assertTrue(leadApplication.getLinkedApplications().stream().anyMatch(linkedApp -> linkedApp.getId().equals(linkedApplication.getId())));
  }
}
