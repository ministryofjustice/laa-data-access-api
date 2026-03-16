package uk.gov.justice.laa.dstew.access.controller.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertCreated;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertProblemRecord;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationOffice;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationOfficeGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.ApplicationCreateRequestIndividualGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualGenerator;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateApplicationTest extends BaseIntegrationTest {
  private static final int applicationVersion = 1;

  private Stream<Arguments> createApplicationTestParameters() {
      return Stream.of(
              Arguments.of(new ApplicationOffice()),
              Arguments.of(DataGenerator.createDefault(ApplicationOfficeGenerator.class))
      );
  }

  @ParameterizedTest
  @MethodSource("createApplicationTestParameters")
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader(
          ApplicationOffice office
  ) throws Exception {
      verifyCreateNewApplication(office, null);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationAndNoOffice_thenReturnCreatedWithLocationHeader() throws Exception {
    verifyCreateNewApplication(null, null);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationWithCivilDecideServiceName_thenReturnCreatedAndPersistServiceName() throws Exception {
    verifyCreateNewApplicationWithServiceName(ServiceName.CIVIL_DECIDE);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_thenReturnCreatedWithLocationHeader() throws Exception {
    final ApplicationEntity leadApplicationToLink = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    final LinkedApplication linkedApplication = LinkedApplication.builder()
        .leadApplicationId(leadApplicationToLink.getApplyApplicationId())
        .associatedApplicationId(UUID.randomUUID())
        .build();
    final var createdEntity = verifyCreateNewApplication(null, linkedApplication);
    final ApplicationEntity leadApplication = applicationRepository.findById(leadApplicationToLink.getId()).orElseThrow();
    assertLinkedApplicationCorrectlyApplied(leadApplication, createdEntity);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_raiseIfLeadNotFound() throws Exception {
    // given
    UUID notFoundLeadId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();

    LinkedApplication linkedApplication = LinkedApplication.builder()
        .leadApplicationId(notFoundLeadId)
        .associatedApplicationId(associatedApplicationId)
        .build();

    ApplicationContent content = DataGenerator.createDefault(ApplicationContentGenerator.class,
        builder -> builder.allLinkedApplications(List.of(linkedApplication))
                          .id(associatedApplicationId)
    );

    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(objectMapper.convertValue(content, Map.class))
    );

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

    // then
    assertSecurityHeaders(result);
    assertNotFound(result);
    assertEquals("application/problem+json", result.getResponse().getContentType());
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("Linking failed > Lead application not found, ID: " + notFoundLeadId, problemDetail.getDetail());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_raiseIfAssociatedNotFound() throws Exception {
    // given
    UUID leadApplicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();
    UUID missingLinkedApplication = UUID.randomUUID();

    persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        applicationEntityBuilder -> applicationEntityBuilder.applyApplicationId(leadApplicationId)
    );

    LinkedApplication linkedApplication = LinkedApplication.builder()
        .leadApplicationId(leadApplicationId)
        .associatedApplicationId(associatedApplicationId)
        .build();

    LinkedApplication invalidLinkedApplication = LinkedApplication.builder()
        .leadApplicationId(leadApplicationId)
        .associatedApplicationId(missingLinkedApplication)
        .build();

    ApplicationContent content = DataGenerator.createDefault(ApplicationContentGenerator.class,
        builder -> builder.allLinkedApplications(List.of(linkedApplication, invalidLinkedApplication))
                          .id(associatedApplicationId)
    );

    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(objectMapper.convertValue(content, Map.class))
    );

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

    // then
    assertSecurityHeaders(result);
    assertNotFound(result);
    assertEquals("application/problem+json", result.getResponse().getContentType());
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("No linked application found with associated apply ids: " + List.of(missingLinkedApplication), problemDetail.getDetail());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenDuplicateApplyApplicationId_whenCreateApplication_thenReturnBadRequest() throws Exception {
    // given
    ApplicationEntity existingApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    ApplicationContent content = DataGenerator.createDefault(ApplicationContentGenerator.class,
        builder -> builder.id(existingApplication.getApplyApplicationId())
    );

    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(objectMapper.convertValue(content, Map.class))
    );

    ProblemDetail expectedProblemDetail = ProblemDetailBuilder.create()
        .status(HttpStatus.BAD_REQUEST)
        .title("Bad Request")
        .detail("Generic Validation Error")
        .build();

    expectedProblemDetail.setProperty("errors",
        List.of("Application already exists for Apply Application Id: " + existingApplication.getApplyApplicationId()));

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
    ProblemDetail detail = deserialise(result, ProblemDetail.class);

    // then
    assertSecurityHeaders(result);
    assertProblemRecord(HttpStatus.BAD_REQUEST, expectedProblemDetail, result, detail);
  }

  private ApplicationEntity verifyCreateNewApplication(ApplicationOffice office, LinkedApplication linkedApplication) throws Exception {
    ApplicationContent content = DataGenerator.createDefault(ApplicationContentGenerator.class,
        builder -> builder.office(office)
                          .allLinkedApplications(linkedApplication == null ? null : List.of(linkedApplication))
                          .id(linkedApplication == null ? UUID.randomUUID() : linkedApplication.getAssociatedApplicationId())
    );

    ApplicationCreateRequest applicationCreateRequest = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(objectMapper.convertValue(content, Map.class))
    );

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

  private void verifyCreateNewApplicationWithServiceName(ServiceName serviceName) throws Exception {
    ApplicationContent content = DataGenerator.createDefault(ApplicationContentGenerator.class);
    ApplicationCreateRequest applicationCreateRequest = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(objectMapper.convertValue(content, Map.class))
    );

    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest, ServiceNameHeader(serviceName.getValue()));

    assertSecurityHeaders(result);
    assertCreated(result);

    UUID createdApplicationId = HeaderUtils.GetUUIDFromLocation(result.getResponse().getHeader("Location"));
    ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId)
      .orElseThrow(() -> new ResourceNotFoundException(createdApplicationId.toString()));

    domainEventAsserts.assertDomainEventForApplication(createdApplication, DomainEventType.APPLICATION_CREATED, serviceName);
  }

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    public void givenCreateNewApplication_whenCreateApplicationAndInvalidServiceNameHeader_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
      verifyBadServiceNameHeader(serviceName);
    }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenCreateNewApplication_whenCreateApplicationAndNoServiceNameHeader_thenReturnBadRequest() throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
      ApplicationCreateRequest applicationCreateRequest = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);

      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION,
              applicationCreateRequest,
              ServiceNameHeader(serviceName));
      applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

  @ParameterizedTest
  @MethodSource("applicationCreateRequestInvalidDataCases")
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
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
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenInvalidApplicationContent_EmptyMap_whenCreateApplication_thenReturnBadRequest() throws Exception {
    ApplicationCreateRequest applicationCreateRequest = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(new HashMap<>())
    );


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
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenInvalidApplicationContent_whenCreateApplication_thenReturnBadRequest() throws Exception {
    ApplicationCreateRequest applicationCreateRequest = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
        builder -> builder.applicationContent(null)
    );

    Map<String, String> invalidFields = new HashMap<>();
    invalidFields.put("applicationContent", "must not be null");

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
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
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
  @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
  public void givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden() throws Exception {
    // given
    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised() throws Exception {
    // given
    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class);

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
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.status(null)),
            problemDetail, Map.of("invalidFields", Map.of("status", mustNotBeNull))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.laaReference(null)),
            problemDetail, Map.of("invalidFields", Map.of("laaReference", mustNotBeNull))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.applicationContent(null)),
            problemDetail, Map.of("invalidFields", Map.of("applicationContent", minimumSizErrorMessage))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.applicationContent(new HashMap<>())),
            problemDetail, Map.of("invalidFields", Map.of("applicationContent", minimumSizErrorMessage))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.applicationContent(Map.of("applicationContent", Map.of("proceedings", List.of())))),
            ProblemDetailBuilder.create().status(HttpStatus.BAD_REQUEST).title("Bad Request").detail("Generic Validation Error")
                .build(), Map.of("errors", List.of("id: must not be null",
            "submittedAt: must not be null"))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(null)),
            problemDetail, Map.of("invalidFields", Map.of("individuals", "size must be between 1 and 2147483647"))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of())),
            problemDetail, Map.of("invalidFields", Map.of("individuals", minimumSizErrorMessage))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of(
                DataGenerator.createDefault(ApplicationCreateRequestIndividualGenerator.class,
                    indBuilder -> indBuilder.dateOfBirth(null))
            ))),
            problemDetail, Map.of("invalidFields", Map.of("individuals[0].dateOfBirth", mustNotBeNull))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of(
                DataGenerator.createDefault(ApplicationCreateRequestIndividualGenerator.class,
                    indBuilder -> indBuilder.details(null))
            ))),
            problemDetail, Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of(
                DataGenerator.createDefault(ApplicationCreateRequestIndividualGenerator.class,
                    indBuilder -> indBuilder.details(new HashMap<>()))
            ))),
            problemDetail, Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of(
                DataGenerator.createDefault(ApplicationCreateRequestIndividualGenerator.class,
                    indBuilder -> indBuilder.dateOfBirth(null).firstName("").lastName("").type(null)
                        .details(new HashMap<>()))
            ))),
            problemDetail, Map.of("invalidFields",
                Map.of(
                    "individuals[0].details", minimumSizErrorMessage,
                    "individuals[0].type", mustNotBeNull,
                    "individuals[0].dateOfBirth", mustNotBeNull))),
        Arguments.of(DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
            builder -> builder.individuals(List.of(
                DataGenerator.createDefault(ApplicationCreateRequestIndividualGenerator.class,
                    indBuilder -> indBuilder.dateOfBirth(null).firstName(null).lastName(null).details(null))
            ))),
            problemDetail, Map.of("invalidFields",
                Map.of("individuals[0].details", mustNotBeNull,
                    "individuals[0].lastName", mustNotBeNull,
                    "individuals[0].firstName", mustNotBeNull,
                    "individuals[0].dateOfBirth", mustNotBeNull
                )
            )));
  }


  private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual)
      throws JacksonException {
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
    assertEquals(1, leadApplication.getLinkedApplications().stream()
        .filter(linkedApp -> linkedApp.getId().equals(linkedApplication.getId()))
        .count());
  }
}
