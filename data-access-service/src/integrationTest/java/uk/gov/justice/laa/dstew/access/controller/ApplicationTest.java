package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Getter;
import org.junit.jupiter.api.Nested;
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
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.ProblemDetailBuilder;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {
  private static final int applicationVersion = 1;

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class GetApplication {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData()
        throws Exception {
      // given
      ApplicationEntity application = persistedApplicationFactory.createAndPersist();
      Application expectedApplication = createApplication(application);

      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());
      Application actualApplication = deserialise(result, Application.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertThat(actualApplication).isEqualTo(expectedApplication);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationNotExist_whenGetApplication_thenReturnNotFound() throws Exception {
      // given
      persistedApplicationFactory.createAndPersist();
      UUID notExistApplicationId = UUID.randomUUID();

      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, notExistApplicationId);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNotFound(result);
      assertEquals("application/problem+json", result.getResponse().getContentType());
      ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
      assertEquals(
          "No application found with id: " + notExistApplicationId, problemDetail.getDetail());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenUnknownRole_whenGetApplication_thenReturnForbidden() throws Exception {
      // given
      ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
      // given
      ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }

    private Application createApplication(ApplicationEntity applicationEntity) {
      Application application = new Application();
      application.setId(applicationEntity.getId());
      application.setApplicationContent(applicationEntity.getApplicationContent());
      application.setStatus(applicationEntity.getStatus());
      application.setSchemaVersion(applicationEntity.getSchemaVersion());
      if (applicationEntity.getCaseworker() != null) {
        application.setCaseworkerId(applicationEntity.getCaseworker().getId());
      }
      if (applicationEntity.getIndividuals() != null) {
        List<Individual> individuals =
            applicationEntity.getIndividuals().stream()
                .map(
                    individualEntity -> {
                      Individual individual = new Individual();
                      individual.setFirstName(individualEntity.getFirstName());
                      individual.setLastName(individualEntity.getLastName());
                      individual.setDateOfBirth(individualEntity.getDateOfBirth());
                      individual.setDetails(individualEntity.getIndividualContent());
                      individual.setType(individualEntity.getType());
                      return individual;
                    })
                .collect(Collectors.toList());
        application.setIndividuals(individuals);
      }
      application.setCreatedAt(
          OffsetDateTime.ofInstant(applicationEntity.getCreatedAt(), ZoneOffset.UTC));
      application.setUpdatedAt(
          OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
      return application;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CreateApplication {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader()
            throws Exception {

      // given
      ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create();

      // when
      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);

      // then
      assertSecurityHeaders(result);
      assertCreated(result);

      UUID createdApplicationId =
          HeaderUtils.GetUUIDFromLocation(result.getResponse().getHeader("Location"));
      assertNotNull(createdApplicationId);
      ApplicationEntity createdApplication =
          applicationRepository
              .findById(createdApplicationId)
              .orElseThrow(() -> new ResourceNotFoundException(createdApplicationId.toString()));
      assertApplicationEqual(applicationCreateRequest, createdApplication);

      assertDomainEventForApplication(createdApplication, DomainEventType.APPLICATION_CREATED);
    }

    @ParameterizedTest
    @MethodSource("applicationCreateRequestInvalidDataCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenInvalidApplicationRequestData_whenCreateApplication_thenReturnBadRequest(
        ApplicationCreateRequest request,
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
    public void givenInvalidApplicationContent_EmptyMap_whenCreateApplication_thenReturnBadRequest()
        throws Exception {
      ApplicationCreateRequest applicationCreateRequest =
          applicationCreateRequestFactory.create(
              builder -> {
                builder.applicationContent(new HashMap<>());
              });

      Map<String, String> invalidFields = new HashMap<>();
      invalidFields.put("applicationContent", "size must be between 1 and " + Integer.MAX_VALUE);

      ProblemDetail expectedProblemDetail =
          ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
      expectedProblemDetail.setProperty("invalidFields", invalidFields);
      // when
      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
      ProblemDetail validationException = deserialise(result, ProblemDetail.class);

      // then
      assertSecurityHeaders(result);
      assertProblemRecord(
          HttpStatus.BAD_REQUEST, expectedProblemDetail, result, validationException);
      assertEquals(0, applicationRepository.count());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenInvalidApplicationContent_whenCreateApplication_thenReturnBadRequest()
        throws Exception {
      ApplicationCreateRequest applicationCreateRequest =
          applicationCreateRequestFactory.create(
              builder -> {
                builder.applicationContent(null);
              });

      Map<String, String> invalidFields = new HashMap<>();
      invalidFields.put("applicationContent", "size must be between 1 and " + Integer.MAX_VALUE);

      ProblemDetail expectedProblemDetail =
          ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
      expectedProblemDetail.setProperty("invalidFields", invalidFields);
      // when
      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
      ProblemDetail validationException = deserialise(result, ProblemDetail.class);

      // then
      assertSecurityHeaders(result);
      assertProblemRecord(
          HttpStatus.BAD_REQUEST, expectedProblemDetail, result, validationException);
      assertEquals(0, applicationRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}"})
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenNoRequestBody_whenCreateApplication_thenReturnBadRequest(String request)
        throws Exception {
      // when
      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
      ProblemDetail detail = deserialise(result, ProblemDetail.class);

      // then
      assertSecurityHeaders(result);
      assertProblemRecord(
          HttpStatus.BAD_REQUEST,
          "Bad Request",
          "Invalid data type for field 'unknown'. Expected: ApplicationCreateRequest.",
          result,
          detail,
          null);
      assertEquals(0, applicationRepository.count());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden()
        throws Exception {
      // given
      ApplicationCreateRequest request = applicationCreateRequestFactory.create();

      // when
      MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    public void
        givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised()
            throws Exception {
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
          ProblemDetailBuilder.create()
              .status(HttpStatus.BAD_REQUEST)
              .title("Bad Request")
              .detail("Request validation failed")
              .build();
      problemDetail.setType(null);
      String minimumSizErrorMessage = "size must be between 1 and " + Integer.MAX_VALUE;
      String mustNotBeNull = "must not be null";

      return Stream.of(
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder -> {
                    builder.status(null);
                  }),
              problemDetail,
              Map.of("invalidFields", Map.of("status", mustNotBeNull))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder -> {
                    builder.laaReference(null);
                  }),
              problemDetail,
              Map.of("invalidFields", Map.of("laaReference", mustNotBeNull))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder -> {
                    builder.individuals(null);
                  }),
              problemDetail,
              Map.of(
                  "invalidFields", Map.of("individuals", "size must be between 1 and 2147483647"))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder -> {
                    builder.individuals(List.of());
                  }),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals", minimumSizErrorMessage))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder -> individualBuilder.firstName(""))))),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals[0].firstName", minimumSizErrorMessage))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder -> individualBuilder.lastName(""))))),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals[0].lastName", minimumSizErrorMessage))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder -> individualBuilder.dateOfBirth(null))))),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals[0].dateOfBirth", mustNotBeNull))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder -> individualBuilder.details(null))))),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder ->
                                      individualBuilder.details(new HashMap<>()))))),
              problemDetail,
              Map.of("invalidFields", Map.of("individuals[0].details", minimumSizErrorMessage))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder ->
                                      individualBuilder
                                          .dateOfBirth(null)
                                          .firstName("")
                                          .lastName("")
                                          .type(null)
                                          .details(new HashMap<>()))))),
              problemDetail,
              Map.of(
                  "invalidFields",
                  Map.of(
                      "individuals[0].details",
                      minimumSizErrorMessage,
                      "individuals[0].lastName",
                      minimumSizErrorMessage,
                      "individuals[0].firstName",
                      minimumSizErrorMessage,
                      "individuals[0].type",
                      mustNotBeNull,
                      "individuals[0].dateOfBirth",
                      mustNotBeNull))),
          Arguments.of(
              applicationCreateRequestFactory.create(
                  builder ->
                      builder.individuals(
                          List.of(
                              individualFactory.create(
                                  individualBuilder ->
                                      individualBuilder
                                          .dateOfBirth(null)
                                          .firstName(null)
                                          .lastName(null)
                                          .details(null))))),
              problemDetail,
              Map.of(
                  "invalidFields",
                  Map.of(
                      "individuals[0].details",
                      minimumSizErrorMessage,
                      "individuals[0].lastName",
                      mustNotBeNull,
                      "individuals[0].firstName",
                      mustNotBeNull,
                      "individuals[0].dateOfBirth",
                      mustNotBeNull))));
    }

    private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual)
        throws JsonProcessingException {
      assertNotNull(actual.getId());

      JsonNode expectedContentNode =
          objectMapper.readTree(objectMapper.writeValueAsString(expected.getApplicationContent()));
      JsonNode actualContentNode =
          objectMapper.readTree(objectMapper.writeValueAsString(actual.getApplicationContent()));

      assertEquals(expectedContentNode, actualContentNode);

      assertEquals(expected.getLaaReference(), actual.getLaaReference());
      assertEquals(expected.getStatus(), actual.getStatus());
      assertEquals(applicationVersion, actual.getSchemaVersion());

      assertNotNull(actual.getSubmittedAt());
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class UpdateApplication {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenUpdateRequestWithNewContentAndStatus_whenUpdateApplication_thenReturnOK_andUpdateApplication()
            throws Exception {
      // given
      ApplicationEntity applicationEntity =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              });

      Map<String, Object> expectedContent = new HashMap<>(Map.of("test", "changed"));

      ApplicationUpdateRequest applicationUpdateRequest =
          applicationUpdateRequestFactory.create(
              builder -> {
                builder.applicationContent(expectedContent).status(ApplicationStatus.SUBMITTED);
              });

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION,
              applicationUpdateRequest,
              applicationEntity.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNoContent(result);

      ApplicationEntity actual =
          applicationRepository.findById(applicationEntity.getId()).orElseThrow();
      assertThat(expectedContent)
          .usingRecursiveComparison()
          .ignoringCollectionOrder()
          .isEqualTo(actual.getApplicationContent());
      assertEquals(ApplicationStatus.SUBMITTED, actual.getStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidApplicationUpdateRequestCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenUpdateRequestWithInvalidContent_whenUpdateApplication_thenReturnBadRequest(
        ApplicationUpdateRequest applicationUpdateRequest) throws Exception {
      // given
      ApplicationEntity applicationEntity =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              });

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION,
              applicationUpdateRequest,
              applicationEntity.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertBadRequest(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenUpdateRequestWithWrongId_whenUpdateApplication_thenReturnNotFound()
        throws Exception {
      // given
      ApplicationEntity applicationEntity =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              });

      ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNotFound(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"f8c3de3d-1fea-4d7c-a8b0", "not a UUID"})
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenUpdateRequestWithInvalidId_whenUpdateApplication_thenReturnNotFound(
        String uuid) throws Exception {
      // given
      persistedApplicationFactory.createAndPersist(
          builder -> {
            builder.applicationContent(new HashMap<>(Map.of("test", "content")));
          });

      ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

      // when
      MvcResult result =
          patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, uuid);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertBadRequest(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenReaderRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
      // given
      ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION,
              applicationUpdateRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenUnknownRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
      // given
      ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION,
              applicationUpdateRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenUpdateApplication_thenReturnUnauthorised() throws Exception {
      // given
      ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

      // when
      MvcResult result =
          patchUri(
              TestConstants.URIs.UPDATE_APPLICATION,
              applicationUpdateRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }

    private Stream<Arguments> invalidApplicationUpdateRequestCases() {
      return Stream.of(
          Arguments.of(
              applicationUpdateRequestFactory.create(builder -> builder.applicationContent(null))),
          null);
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class AssignCaseworker {

    @ParameterizedTest
    @MethodSource("validAssignCaseworkerRequestCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidAssignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker(
        AssignCaseworkerCase assignCaseworkerCase) throws Exception {
      // given
      List<ApplicationEntity> expectedAssignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              assignCaseworkerCase.numberOfApplicationsToAssign,
              builder -> {
                builder.caseworker(null);
              });

      List<ApplicationEntity> expectedAlreadyAssignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      List<ApplicationEntity> expectedUnassignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              assignCaseworkerCase.numberOfApplicationsNotAssigned,
              builder -> {
                builder.caseworker(null);
              });

      CaseworkerAssignRequest caseworkerAssignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                builder
                    .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                    .applicationIds(
                        expectedAssignedApplications.stream()
                            .map(ApplicationEntity::getId)
                            .collect(Collectors.toList())
                            .reversed())
                    .eventHistory(
                        EventHistory.builder().eventDescription("Assigning caseworker").build());
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertApplicationsMatchInRepository(expectedAssignedApplications);
      assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
      assertApplicationsMatchInRepository(expectedUnassignedApplications);
      assertDomainEventsCreatedForApplications(
          expectedAssignedApplications,
          BaseIntegrationTest.CaseworkerJohnDoe.getId(),
          DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
          caseworkerAssignRequest.getEventHistory());
    }

    @ParameterizedTest
    @MethodSource("invalidAssignCaseworkerRequestCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenInvalidAssignRequestBecauseApplicationDoesNotExist_whenAssignCaseworker_thenReturnNotFound_andGiveMissingIds(
            AssignCaseworkerCase assignCaseworkerCase) throws Exception {
      // given
      List<ApplicationEntity> expectedAlreadyAssignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      List<ApplicationEntity> expectedUnassignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              assignCaseworkerCase.numberOfApplicationsNotAssigned,
              builder -> {
                builder.caseworker(null);
              });

      List<UUID> invalidApplicationIds =
          IntStream.range(0, assignCaseworkerCase.numberOfApplicationsToAssign)
              .mapToObj(i -> UUID.randomUUID())
              .toList();

      // generate random UUIDs so simulate records that do not exist..
      CaseworkerAssignRequest caseworkerAssignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                ;
                builder
                    .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                    .applicationIds(invalidApplicationIds);
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNotFound(result);

      ProblemDetail problemResult = deserialise(result, ProblemDetail.class);
      // Extract UUIDs from the detail string
      Pattern uuidPattern = Pattern.compile("[0-9a-fA-F\\-]{36}");
      Set<UUID> actualIds =
          uuidPattern
              .matcher(problemResult.getDetail())
              .results()
              .map(MatchResult::group)
              .map(UUID::fromString)
              .collect(Collectors.toSet());

      assertEquals(new HashSet<>(invalidApplicationIds), actualIds);

      assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
      assertApplicationsMatchInRepository(expectedUnassignedApplications);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenInvalidAssignRequestBecauseSomeApplicationsDoNotExist_whenAssignCaseworker_thenReturnNotFound_andAssignAvailableApplications_andGiveMissingIds()
            throws Exception {
      // given
      List<ApplicationEntity> expectedAssignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              4,
              builder -> {
                builder.caseworker(null);
              });

      List<UUID> invalidApplicationIds =
          IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID()).toList();

      List<UUID> allApplicationIds =
          Stream.of(
                  expectedAssignedApplications.stream().map(ApplicationEntity::getId),
                  invalidApplicationIds.stream())
              .flatMap(s -> s)
              .collect(Collectors.toList());

      // generate random UUIDs so simulate records that do not exist..
      CaseworkerAssignRequest caseworkerAssignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                ;
                builder
                    .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                    .applicationIds(allApplicationIds);
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNotFound(result);

      ProblemDetail problemResult = deserialise(result, ProblemDetail.class);
      // Extract UUIDs from the detail string
      Pattern uuidPattern = Pattern.compile("[0-9a-fA-F\\-]{36}");
      Set<UUID> actualIds =
          uuidPattern
              .matcher(problemResult.getDetail())
              .results()
              .map(MatchResult::group)
              .map(UUID::fromString)
              .collect(Collectors.toSet());

      assertEquals(new HashSet<>(invalidApplicationIds), actualIds);

      assertApplicationsMatchInRepository(expectedAssignedApplications);
    }

    @ParameterizedTest
    @MethodSource("invalidApplicationIdListsCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenInvalidAssignmentRequestBecauseInvalidApplicationIds_whenAssignCaseworker_thenReturnBadRequest(
            List<UUID> invalidApplicationIdList) throws Exception {
      // given
      ApplicationEntity expectedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(null);
              });

      CaseworkerAssignRequest caseworkerAssignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId());
                builder.applicationIds(invalidApplicationIdList);
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertBadRequest(result);

      ApplicationEntity actualApplication =
          applicationRepository.findById(expectedApplication.getId()).orElseThrow();
      assertNull(actualApplication.getCaseworker());
      assertEquals(expectedApplication, actualApplication);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenInvalidAssignmentRequestBecauseCaseworkerDoesNotExist_whenAssignCaseworker_thenReturnBadRequest()
            throws Exception {
      // given
      ApplicationEntity expectedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(null);
              });

      CaseworkerAssignRequest caseworkerAssignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                builder.caseworkerId(null);
                builder.applicationIds(List.of(expectedApplication.getId()));
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertBadRequest(result);

      ApplicationEntity actualApplication =
          applicationRepository.findById(expectedApplication.getId()).orElseThrow();
      assertNull(actualApplication.getCaseworker());
      assertEquals(expectedApplication, actualApplication);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenReaderRole_whenAssignCaseworker_thenReturnForbidden() throws Exception {
      // given
      CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenUnknownRole_whenAssignCaseworker_thenReturnForbidden() throws Exception {
      // given
      CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenAssignCaseworker_thenReturnUnauthorised() throws Exception {
      // given
      CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }

    @Getter
    private class AssignCaseworkerCase {
      Integer numberOfApplicationsToAssign;
      Integer numberOfApplicationsAlreadyAssigned;
      Integer numberOfApplicationsNotAssigned;

      public AssignCaseworkerCase(
          Integer numberOfApplicationsToAssign,
          Integer numberOfApplicationsAlreadyAssigned,
          Integer numberOfApplicationsNotAssigned) {
        this.numberOfApplicationsToAssign = numberOfApplicationsToAssign;
        this.numberOfApplicationsAlreadyAssigned = numberOfApplicationsAlreadyAssigned;
        this.numberOfApplicationsNotAssigned = numberOfApplicationsNotAssigned;
      }
    }

    private Stream<Arguments> validAssignCaseworkerRequestCases() {
      return Stream.of(
          Arguments.of(new AssignCaseworkerCase(3, 3, 2)),
          Arguments.of(new AssignCaseworkerCase(5, 0, 4)),
          Arguments.of(new AssignCaseworkerCase(2, 4, 0)));
    }

    private Stream<Arguments> invalidAssignCaseworkerRequestCases() {
      return Stream.of(
          Arguments.of(new AssignCaseworkerCase(5, 3, 2)),
          Arguments.of(new AssignCaseworkerCase(2, 0, 4)),
          Arguments.of(new AssignCaseworkerCase(4, 4, 0)));
    }

    private Stream<Arguments> invalidApplicationIdListsCases() {
      return Stream.of(
          Arguments.of(Collections.emptyList()),
          Arguments.of((Object) null),
          Arguments.of(Arrays.asList(new UUID[] {UUID.randomUUID(), null})));
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class UnassignCaseworker {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenValidUnassignRequest_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
            throws Exception {
      // given
      ApplicationEntity expectedUnassignedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create(
              builder -> {
                builder.eventHistory(
                    EventHistory.builder().eventDescription("Unassigned Caseworker").build());
              });

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              expectedUnassignedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      ApplicationEntity actual =
          applicationRepository.findById(expectedUnassignedApplication.getId()).orElseThrow();
      assertNull(actual.getCaseworker());
      assertEquals(expectedUnassignedApplication, actual);

      assertDomainEventsCreatedForApplications(
          List.of(expectedUnassignedApplication),
          null,
          DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
          caseworkerUnassignRequest.getEventHistory());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenValidUnassignRequestWithBlankEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
            throws Exception {
      // given
      ApplicationEntity expectedUnassignedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create(
              builder -> {
                builder.eventHistory(EventHistory.builder().eventDescription("").build());
              });

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              expectedUnassignedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      ApplicationEntity actual =
          applicationRepository.findById(expectedUnassignedApplication.getId()).orElseThrow();
      assertNull(actual.getCaseworker());
      assertEquals(expectedUnassignedApplication, actual);

      assertDomainEventsCreatedForApplications(
          List.of(expectedUnassignedApplication),
          null,
          DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
          caseworkerUnassignRequest.getEventHistory());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void
        givenValidUnassignRequestWithNullEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
            throws Exception {
      // given
      ApplicationEntity expectedUnassignedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create(
              builder -> {
                builder.eventHistory(EventHistory.builder().eventDescription(null).build());
              });

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              expectedUnassignedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertDomainEventsCreatedForApplications(
          List.of(expectedUnassignedApplication),
          null,
          DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
          caseworkerUnassignRequest.getEventHistory());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenApplicationNotExist_whenUnassignCaseworker_thenReturnNotFound()
        throws Exception {
      // given
      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create();

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertNotFound(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenCaseworkerNotExist_whenUnassignCaseworker_thenReturnOK() throws Exception {
      // given
      ApplicationEntity expectedUnassignedApplication =
          persistedApplicationFactory.createAndPersist(
              builder -> {
                builder.caseworker(null);
              });

      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create(
              builder -> {
                ;
                builder.eventHistory(
                    EventHistory.builder().eventDescription("Unassigned Caseworker").build());
              });

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              expectedUnassignedApplication.getId());

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      // TODO: is this right?
      assertOK(result);

      List<DomainEventEntity> domainEventEntities = domainEventRepository.findAll();
      assertEquals(0, domainEventEntities.size());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenReaderRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
      // given
      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create();

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenUnknownRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
      // given
      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create();

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised() throws Exception {
      // given
      CaseworkerUnassignRequest caseworkerUnassignRequest =
          caseworkerUnassignRequestFactory.create();

      // when
      MvcResult result =
          postUri(
              TestConstants.URIs.UNASSIGN_CASEWORKER,
              caseworkerUnassignRequest,
              UUID.randomUUID().toString());

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }
  }

  // invalid reassign is covered by invalid assign tests
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ReassignCaseworker {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValiReassignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker()
        throws Exception {
      // given
      List<ApplicationEntity> expectedReassignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              4,
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
              });

      List<ApplicationEntity> expectedAlreadyAssignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              5,
              builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe);
              });

      List<ApplicationEntity> expectedUnassignedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              8,
              builder -> {
                builder.caseworker(null);
              });

      CaseworkerAssignRequest caseworkerReassignRequest =
          caseworkerAssignRequestFactory.create(
              builder -> {
                builder
                    .caseworkerId(BaseIntegrationTest.CaseworkerJaneDoe.getId())
                    .applicationIds(
                        expectedReassignedApplications.stream()
                            .map(ApplicationEntity::getId)
                            .collect(Collectors.toList()))
                    .eventHistory(
                        EventHistory.builder().eventDescription("Assigning caseworker").build());
              });

      // when
      MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerReassignRequest);

      // then
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertApplicationsMatchInRepository(expectedReassignedApplications);
      assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
      assertApplicationsMatchInRepository(expectedUnassignedApplications);
      assertDomainEventsCreatedForApplications(
          expectedReassignedApplications,
          BaseIntegrationTest.CaseworkerJaneDoe.getId(),
          DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
          caseworkerReassignRequest.getEventHistory());
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class GetApplications {

    public static final String SEARCH_PAGE_PARAM = "page=";
    public static final String SEARCH_PAGE_SIZE_PARAM = "pageSize=";
    public static final String SEARCH_STATUS_PARAM = "status=";
    public static final String SEARCH_FIRSTNAME_PARAM = "clientFirstName=";
    public static final String SEARCH_LASTNAME_PARAM = "clientLastName=";
    public static final String SEARCH_CASEWORKERID_PARAM = "userId=";

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly()
            throws Exception {
      // given
      List<ApplicationEntity> expectedApplicationsWithCaseworker =
          persistedApplicationFactory.createAndPersistMultiple(
              3, builder -> builder.status(ApplicationStatus.IN_PROGRESS));
      List<ApplicationEntity> expectedApplicationWithDifferentCaseworker =
          persistedApplicationFactory.createAndPersistMultiple(
              3,
              builder ->
                  builder.status(ApplicationStatus.IN_PROGRESS).caseworker(CaseworkerJaneDoe));
      List<ApplicationEntity> expectedApplicationWithNoCaseworker =
          persistedApplicationFactory.createAndPersistMultiple(
              3, builder -> builder.status(ApplicationStatus.IN_PROGRESS).caseworker(null));

      List<ApplicationSummary> expectedApplicationsSummary =
          Stream.of(
                  expectedApplicationsWithCaseworker,
                  expectedApplicationWithDifferentCaseworker,
                  expectedApplicationWithNoCaseworker)
              .flatMap(List::stream)
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 9, 10, 1, 9);
      assertThat(actual.getApplications().size()).isEqualTo(9);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsRequiringPageTwo_whenGetApplications_thenReturnSecondPageOfApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(
                  20, builder -> builder.status(ApplicationStatus.IN_PROGRESS))
              .stream()
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result =
          getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "2");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 20, 10, 2, 10);
      assertThat(actual.getApplications().size()).isEqualTo(10);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 20)));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsAndPageSizeOfTwenty_whenGetApplications_thenReturnTwentyRecords()
        throws Exception {
      // given
      List<ApplicationEntity> inProgressApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              15, builder -> builder.status(ApplicationStatus.IN_PROGRESS));
      List<ApplicationEntity> submittedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              10, builder -> builder.status(ApplicationStatus.SUBMITTED));

      List<ApplicationSummary> expectedApplicationsSummary =
          Stream.concat(inProgressApplications.stream(), submittedApplications.stream().limit(5))
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result =
          getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 25, 20, 1, 20);
      assertThat(actual.getApplications().size()).isEqualTo(20);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @ParameterizedTest
    @MethodSource("applicationsSummaryFilteredByStatusCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            ApplicationStatus Status,
            Supplier<List<ApplicationSummary>> expectedApplicationsSummarySupplier,
            int numberOfApplications)
            throws Exception {
      // given
      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplicationsSummarySupplier.get();

      // when
      MvcResult result =
          getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + Status);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, numberOfApplications, 10, 1, numberOfApplications);
      assertThat(actual.getApplications().size()).isEqualTo(numberOfApplications);
      assertTrue((actual.getApplications()).containsAll(expectedApplicationsSummary));
    }

    // TODO: is this test superseded by parameterized test above?
    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(5, builder -> builder.status(ApplicationStatus.IN_PROGRESS))
              .stream()
              .map(this::createApplicationSummary)
              .toList();

      persistedApplicationFactory.createAndPersistMultiple(
          10, builder -> builder.status(ApplicationStatus.SUBMITTED));

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.IN_PROGRESS);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 5, 10, 1, 5);
      assertThat(actual.getApplications().size()).isEqualTo(5);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    // TODO: is this test superseded by parameterized test above?
    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(6, builder -> builder.status(ApplicationStatus.SUBMITTED))
              .stream()
              .map(this::createApplicationSummary)
              .toList();
      persistedApplicationFactory.createAndPersistMultiple(
          10, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.SUBMITTED);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 6, 10, 1, 6);
      assertThat(actual.getApplications().size()).isEqualTo(6);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredBySubmittedStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(17, builder -> builder.status(ApplicationStatus.SUBMITTED))
              .stream()
              .map(this::createApplicationSummary)
              .toList();
      persistedApplicationFactory.createAndPersistMultiple(
          10, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.SUBMITTED
                  + "&"
                  + SEARCH_PAGE_PARAM
                  + "2");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 17, 10, 2, 7);
      assertThat(actual.getApplications().size()).isEqualTo(7);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 17)));
    }

    @ParameterizedTest
    @MethodSource("firstNameSearchCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            String searchFirstName, String persistedFirstName, int expectedCount) throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder.individuals(
                  Set.of(individualEntityFactory.create(i -> i.firstName("John")))));

      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(
                  expectedCount,
                  builder ->
                      builder.individuals(
                          Set.of(
                              individualEntityFactory.create(
                                  i -> i.firstName(persistedFirstName)))))
              .stream()
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + searchFirstName);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, expectedCount, 10, 1, expectedCount);
      assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByFirstNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          8,
          builder ->
              builder
                  .status(ApplicationStatus.IN_PROGRESS)
                  .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Jane")))));

      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(
                  7,
                  builder ->
                      builder
                          .status(ApplicationStatus.SUBMITTED)
                          .individuals(
                              Set.of(individualEntityFactory.create(i -> i.firstName("Jane")))))
              .stream()
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.SUBMITTED
                  + "&"
                  + SEARCH_FIRSTNAME_PARAM
                  + "Jane");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 7, 10, 1, 7);
      assertThat(actual.getApplications().size()).isEqualTo(7);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @ParameterizedTest
    @MethodSource("lastNameSearchCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            String searchLastName, String persistedLastName, int expectedCount) throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder.individuals(
                  Set.of(individualEntityFactory.create(i -> i.lastName("Johnson")))));
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(
                  expectedCount,
                  builder ->
                      builder.individuals(
                          Set.of(
                              individualEntityFactory.create(i -> i.lastName(persistedLastName)))))
              .stream()
              .map(this::createApplicationSummary)
              .toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + searchLastName);
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, expectedCount, 10, 1, expectedCount);
      assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          1,
          builder ->
              builder
                  .status(ApplicationStatus.IN_PROGRESS)
                  .individuals(Set.of(individualEntityFactory.create(i -> i.lastName("David")))));

      List<ApplicationEntity> expectedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              7,
              builder ->
                  builder
                      .status(ApplicationStatus.SUBMITTED)
                      .individuals(
                          Set.of(individualEntityFactory.create(i -> i.lastName("David")))));

      persistedApplicationFactory.createAndPersistMultiple(
          5,
          builder ->
              builder
                  .status(ApplicationStatus.IN_PROGRESS)
                  .individuals(Set.of(individualEntityFactory.create(i -> i.lastName("Smith")))));

      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplications.stream().map(this::createApplicationSummary).toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.SUBMITTED
                  + "&"
                  + SEARCH_LASTNAME_PARAM
                  + "David");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 7, 10, 1, 7);
      assertThat(actual.getApplications().size()).isEqualTo(7);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByFirstNameAndLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("George").lastName("Taylor")))));
      List<ApplicationSummary> expectedApplicationsSummary =
          persistedApplicationFactory
              .createAndPersistMultiple(
                  2,
                  builder ->
                      builder.individuals(
                          Set.of(
                              individualEntityFactory.create(
                                  i -> i.firstName("Lucas").lastName("Taylor")))))
              .stream()
              .map(this::createApplicationSummary)
              .toList();
      persistedApplicationFactory.createAndPersistMultiple(
          5,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Victoria").lastName("Williams")))));

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_FIRSTNAME_PARAM
                  + "Lucas"
                  + "&"
                  + SEARCH_LASTNAME_PARAM
                  + "Taylor");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 2, 10, 1, 2);
      assertThat(actual.getApplications().size()).isEqualTo(2);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByFirstNameAndLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationEntity> expectedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              1,
              builder ->
                  builder
                      .status(ApplicationStatus.IN_PROGRESS)
                      .individuals(
                          Set.of(
                              individualEntityFactory.create(
                                  i -> i.firstName("George").lastName("Theodore")))));

      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder
                  .status(ApplicationStatus.SUBMITTED)
                  .individuals(
                      Set.of(
                          individualEntityFactory.create(
                              i -> i.firstName("George").lastName("Theodore")))));
      persistedApplicationFactory.createAndPersistMultiple(
          2,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Lucas").lastName("Jones")))));
      persistedApplicationFactory.createAndPersistMultiple(
          5,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Victoria").lastName("Theodore")))));

      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplications.stream().map(this::createApplicationSummary).toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.IN_PROGRESS
                  + "&"
                  + SEARCH_FIRSTNAME_PARAM
                  + "George"
                  + "&"
                  + SEARCH_LASTNAME_PARAM
                  + "Theodore");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 1, 10, 1, 1);
      assertThat(actual.getApplications().size()).isEqualTo(1);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByFirstNameAndLastNameAndStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationEntity> expectedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              13,
              builder ->
                  builder
                      .status(ApplicationStatus.IN_PROGRESS)
                      .individuals(
                          Set.of(
                              individualEntityFactory.create(
                                  i -> i.firstName("George").lastName("Theodore")))));

      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder
                  .status(ApplicationStatus.SUBMITTED)
                  .individuals(
                      Set.of(
                          individualEntityFactory.create(
                              i -> i.firstName("George").lastName("Theodore")))));
      persistedApplicationFactory.createAndPersistMultiple(
          2,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Lucas").lastName("Jones")))));
      persistedApplicationFactory.createAndPersistMultiple(
          5,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Victoria").lastName("Theodore")))));

      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplications.stream().map(this::createApplicationSummary).toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.IN_PROGRESS
                  + "&"
                  + SEARCH_FIRSTNAME_PARAM
                  + "George"
                  + "&"
                  + SEARCH_LASTNAME_PARAM
                  + "Theodore"
                  + "&"
                  + SEARCH_PAGE_PARAM
                  + "2");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 13, 10, 2, 3);
      assertThat(actual.getApplications().size()).isEqualTo(3);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 13)));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void
        givenApplicationsFilteredByStatusAndNoApplicationsMatch_whenGetApplications_thenReturnEmptyResult()
            throws Exception {
      // given
      persistedApplicationFactory.createAndPersistMultiple(
          7,
          builder ->
              builder
                  .status(ApplicationStatus.IN_PROGRESS)
                  .individuals(
                      Set.of(
                          individualEntityFactory.create(
                              i -> i.firstName("George").lastName("Theodore")))));
      persistedApplicationFactory.createAndPersistMultiple(
          3,
          builder ->
              builder
                  .status(ApplicationStatus.SUBMITTED)
                  .individuals(
                      Set.of(
                          individualEntityFactory.create(
                              i -> i.firstName("George").lastName("Theodore")))));
      persistedApplicationFactory.createAndPersistMultiple(
          2,
          builder ->
              builder
                  .status(ApplicationStatus.SUBMITTED)
                  .individuals(
                      Set.of(
                          individualEntityFactory.create(
                              i -> i.firstName("Lucas").lastName("Jones")))));
      persistedApplicationFactory.createAndPersistMultiple(
          5,
          builder ->
              builder.individuals(
                  Set.of(
                      individualEntityFactory.create(
                          i -> i.firstName("Victoria").lastName("Theodore")))));

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_STATUS_PARAM
                  + ApplicationStatus.IN_PROGRESS
                  + "&"
                  + SEARCH_FIRSTNAME_PARAM
                  + "Lucas"
                  + "&"
                  + SEARCH_LASTNAME_PARAM
                  + "Jones");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 0, 10, 1, 0);
      assertThat(actual.getApplications().size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void
        givenApplicationsFilteredByCaseworkerJohnDoe_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
            throws Exception {
      // given
      List<ApplicationEntity> expectedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              4, builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

      persistedApplicationFactory.createAndPersistMultiple(
          6, builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe));

      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplications.stream().map(this::createApplicationSummary).toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.GET_APPLICATIONS
                  + "?"
                  + SEARCH_CASEWORKERID_PARAM
                  + BaseIntegrationTest.CaseworkerJohnDoe.getId());
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 4, 10, 1, 4);
      assertThat(actual.getApplications().size()).isEqualTo(4);
      assertArrayEquals(expectedApplicationsSummary.toArray(), actual.getApplications().toArray());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenPageZero_whenGetApplications_thenDefaultToPageOneAndReturnCorrectResults()
        throws Exception {
      // given
      List<ApplicationEntity> expectedApplications =
          persistedApplicationFactory.createAndPersistMultiple(
              15, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

      List<ApplicationSummary> expectedApplicationsSummary =
          expectedApplications.stream().map(this::createApplicationSummary).toList();

      // when
      MvcResult result =
          getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "0");
      ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);
      assertPaging(actual, 15, 10, 1, 10);
      assertThat(actual.getApplications().size()).isEqualTo(10);
      assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(0, 10)));
    }

    @Test
    public void givenNoUser_whenGetApplications_thenReturnUnauthorised() throws Exception {
      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenNoRole_whenGetApplications_thenReturnForbidden() throws Exception {
      // when
      MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    private void assertPaging(
        ApplicationSummaryResponse applicationSummaryResponse,
        Integer totalRecords,
        Integer pageSize,
        Integer page,
        Integer itemsReturned) {
      assertThat(applicationSummaryResponse.getPaging().getTotalRecords()).isEqualTo(totalRecords);
      assertThat(applicationSummaryResponse.getPaging().getPageSize()).isEqualTo(pageSize);
      assertThat(applicationSummaryResponse.getPaging().getPage()).isEqualTo(page);
      assertThat(applicationSummaryResponse.getPaging().getItemsReturned())
          .isEqualTo(itemsReturned);
    }

    private static Stream<Arguments> firstNameSearchCases() {
      return Stream.of(
          Arguments.of("Jane", "Jane", 5),
          Arguments.of("Jan", "Jane", 3),
          Arguments.of("ne", "Jane", 7),
          Arguments.of("an", "Jane", 4),
          Arguments.of("ANE", "Jane", 6));
    }

    private static Stream<Arguments> lastNameSearchCases() {
      return Stream.of(
          Arguments.of("Smith", "Smith", 3),
          Arguments.of("Smi", "Smith", 7),
          Arguments.of("ith", "Smith", 5),
          Arguments.of("mit", "Smith", 8),
          Arguments.of("MITH", "Smith", 4));
    }

    private Stream<Arguments> applicationsSummaryFilteredByStatusCases() {
      return Stream.of(
          Arguments.of(
              ApplicationStatus.IN_PROGRESS,
              (Supplier<List<ApplicationSummary>>)
                  () -> generateApplicationSummaries(ApplicationStatus.IN_PROGRESS, 8),
              8),
          Arguments.of(
              ApplicationStatus.SUBMITTED,
              (Supplier<List<ApplicationSummary>>)
                  () -> generateApplicationSummaries(ApplicationStatus.SUBMITTED, 5),
              5));
    }

    private List<ApplicationSummary> generateApplicationSummaries(
        ApplicationStatus status, int numberOfApplications) {
      Random random = new Random();

      return persistedApplicationFactory
          .createAndPersistMultiple(
              numberOfApplications,
              builder -> builder.status(status).laaReference("REF-00" + random.nextInt(100)))
          .stream()
          .map(this::createApplicationSummary)
          .collect(Collectors.toList());
    }

    private ApplicationSummary createApplicationSummary(ApplicationEntity applicationEntity) {
      ApplicationSummary applicationSummary = new ApplicationSummary();
      applicationSummary.setApplicationId(applicationEntity.getId());
      applicationSummary.setStatus(applicationEntity.getStatus());
      applicationSummary.setSubmittedAt(
          applicationEntity.getSubmittedAt().atOffset(ZoneOffset.UTC));
      applicationSummary.setLastUpdated(applicationEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
      applicationSummary.setUsedDelegatedFunctions(applicationEntity.isUseDelegatedFunctions());
      applicationSummary.setCategoryOfLaw(applicationEntity.getCategoryOfLaw());
      applicationSummary.setMatterType(applicationEntity.getMatterType());
      applicationSummary.setAssignedTo(
          applicationEntity.getCaseworker() != null
              ? applicationEntity.getCaseworker().getId()
              : null);
      applicationSummary.autoGrant(applicationEntity.isAutoGranted());
      applicationSummary.setLaaReference(applicationEntity.getLaaReference());
      applicationSummary.setApplicationType(ApplicationType.INITIAL);
      applicationSummary.setClientFirstName(
          applicationEntity.getIndividuals().stream().findFirst().get().getFirstName());
      applicationSummary.setClientLastName(
          applicationEntity.getIndividuals().stream().findFirst().get().getLastName());
      applicationSummary.setClientDateOfBirth(
          applicationEntity.getIndividuals().stream().findFirst().get().getDateOfBirth());
      return applicationSummary;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class GetDomainEvents {

    private final String SEARCH_EVENT_TYPE_PARAM = "eventType=";

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void
        givenApplicationWithDomainEvents_whenApplicationHistorySearch_theReturnDomainEvents()
            throws Exception {
      var appId = persistedApplicationFactory.createAndPersist().getId();
      // given
      var domainEvents = setUpDomainEvents(appId);
      var expectedDomainEvents = domainEvents.stream().map(GetDomainEvents::toEvent).toList();

      // when
      MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, appId);
      ApplicationHistoryResponse actualResponse =
          deserialise(result, ApplicationHistoryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertThat(actualResponse).isNotNull();
      assertTrue(actualResponse.getEvents().containsAll(expectedDomainEvents));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void
        givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterSingleDomainEvent_thenOnlyFilteredDomainEventTypes()
            throws Exception {
      var appId = persistedApplicationFactory.createAndPersist().getId();
      // given
      var domainEvents = setUpDomainEvents(appId);
      var expectedAssignDomainEvents =
          domainEvents.stream()
              .filter(s -> s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER))
              .map(GetDomainEvents::toEvent)
              .toList();

      // when
      MvcResult result =
          getUri(
              TestConstants.URIs.APPLICATION_HISTORY_SEARCH
                  + "?"
                  + SEARCH_EVENT_TYPE_PARAM
                  + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
              appId);
      ApplicationHistoryResponse actualResponse =
          deserialise(result, ApplicationHistoryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertThat(actualResponse).isNotNull();
      assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void
        givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterMultipleDomainEvent_thenOnlyFilteredDomainEventTypes()
            throws Exception {
      var appId = persistedApplicationFactory.createAndPersist().getId();
      // given
      var domainEvents = setUpDomainEvents(appId);
      var expectedAssignDomainEvents =
          domainEvents.stream()
              .filter(
                  s ->
                      s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                          || s.getType().equals(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER))
              .map(GetDomainEvents::toEvent)
              .toList();

      // when
      String address =
          TestConstants.URIs.APPLICATION_HISTORY_SEARCH
              + "?"
              + SEARCH_EVENT_TYPE_PARAM
              + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER
              + "&"
              + SEARCH_EVENT_TYPE_PARAM
              + DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER;

      MvcResult result = getUri(address, appId);
      ApplicationHistoryResponse actualResponse =
          deserialise(result, ApplicationHistoryResponse.class);

      // then
      assertContentHeaders(result);
      assertSecurityHeaders(result);
      assertNoCacheHeaders(result);
      assertOK(result);

      assertThat(actualResponse).isNotNull();
      assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
    }

    @Test
    public void givenNoUser_whenApplicationHistorySearch_thenReturnUnauthorised() throws Exception {
      // when
      MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

      // then
      assertSecurityHeaders(result);
      assertUnauthorised(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenNoRole_whenApplicationHistorySearch_thenReturnForbidden() throws Exception {
      // when
      MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

      // then
      assertSecurityHeaders(result);
      assertForbidden(result);
    }

    private List<DomainEventEntity> setUpDomainEvents(UUID appId) {
      return List.of(
          setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
          setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
          setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
          setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
          setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));
    }

    private DomainEventEntity setupDomainEvent(UUID appId, DomainEventType eventType) {
      String eventDesc = "{ \"eventDescription\" : \"" + eventType.getValue() + "\"}";
      return persistedDomainEventFactory.createAndPersist(
          builder -> {
            builder.applicationId(appId);
            builder.createdAt(Instant.now());
            builder.data(eventDesc);
            builder.type(eventType);
          });
    }

    private static ApplicationDomainEvent toEvent(DomainEventEntity entity) {
      return ApplicationDomainEvent.builder()
          .applicationId(entity.getApplicationId())
          .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
          .domainEventType(entity.getType())
          .eventDescription(entity.getData())
          .caseworkerId(entity.getCaseworkerId())
          .createdBy(entity.getCreatedBy())
          .build();
    }
  }

  // <editor-fold desc="Shared asserts">

  private void assertApplicationsMatchInRepository(List<ApplicationEntity> expected) {
    List<ApplicationEntity> actual =
        applicationRepository.findAllById(
            expected.stream().map(ApplicationEntity::getId).collect(Collectors.toList()));
    assertThat(expected.size()).isEqualTo(actual.size());
    assertTrue(expected.containsAll(actual));
  }

  private void assertDomainEventsCreatedForApplications(
      List<ApplicationEntity> applications,
      UUID caseWorkerId,
      DomainEventType expectedDomainEventType,
      EventHistory expectedEventHistory) {

    List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

    assertEquals(applications.size(), domainEvents.size());

    List<UUID> applicationIds =
        applications.stream().map(ApplicationEntity::getId).collect(Collectors.toList());

    for (DomainEventEntity domainEvent : domainEvents) {
      assertEquals(expectedDomainEventType, domainEvent.getType());
      assertTrue(applicationIds.contains(domainEvent.getApplicationId()));
      assertEquals(caseWorkerId, domainEvent.getCaseworkerId());
      if (expectedEventHistory.getEventDescription() != null) {
        assertTrue(domainEvent.getData().contains(expectedEventHistory.getEventDescription()));
      } else {
        assertFalse(domainEvent.getData().contains("eventDescription"));
      }
    }
  }

  private void assertDomainEventForApplication(
      ApplicationEntity application, DomainEventType expectedType) throws Exception {

    List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

    DomainEventEntity event = domainEvents.get(0);

    assertThat(event.getApplicationId()).isEqualTo(application.getId());
    assertThat(event.getType()).isEqualTo(expectedType);
    assertThat(event.getCreatedAt()).isNotNull();

    // ---- JSON payload assertions ----
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(event.getData());

    assertThat(json.get("applicationId").asText()).isEqualTo(application.getId().toString());

    assertThat(json.get("applicationStatus").asText()).isEqualTo(application.getStatus().name());

    assertThat(json.get("applicationContent").asText()).contains("{"); // stored as stringified JSON

    if (expectedType == DomainEventType.APPLICATION_CREATED) {

      assertThat(json.has("createdDate")).isTrue();
      assertThat(json.get("createdDate").asText()).isEqualTo(application.getCreatedAt().toString());

    } else if (expectedType == DomainEventType.APPLICATION_UPDATED) {

      assertThat(json.has("updatedDate")).isTrue();
      assertThat(json.get("updatedDate").asText())
          .isEqualTo(application.getModifiedAt().toString());
    }
  }

  // </editor-fold>
}
