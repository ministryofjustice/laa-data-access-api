package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.IndividualAssert.assertIndividualCollectionsEqual;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.records.ProceedingJsonObject;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateApplicationTest extends BaseServiceTest {

  @Autowired
  private ApplicationService serviceUnderTest;


  @Test
  public void givenNewApplication_whenCreateApplication_thenReturnNewId() throws JsonProcessingException {

    // given
    UUID expectedId = UUID.randomUUID();
    ApplicationEntity withExpectedId = applicationEntityFactory.createDefault(builder ->
        builder.id(expectedId)
    );

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault();

    when(applicationRepository.save(any())).thenReturn(withExpectedId);

    DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(expectedId)
        .type(DomainEventType.APPLICATION_CREATED)
        .data(objectMapper.writeValueAsString(CreateApplicationDomainEventDetails.builder()
            .applicationId(expectedId)
            .applicationStatus(ApplicationStatus.IN_PROGRESS.toString())
            .applicationContent(withExpectedId.getApplicationContent().toString())
            .build()))
        .build();

    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    UUID actualId = serviceUnderTest.createApplication(applicationCreateRequest);

    // then
    assertEquals(expectedId, actualId);

    verifyThatApplicationSaved(applicationCreateRequest, 1);
    verifyThatCreateDomainEventSaved(expectedDomainEvent, 1);
  }


  @ParameterizedTest
  @MethodSource("provideProceedingsForMapping")
  void mapToApplicationEntity_SuccessfullyMapFromApplicationContentFields(ApplicationCreateRequest application,
                                                                          boolean expectedUseDelegatedFunctions) {
    // Given
    setSecurityContext(TestConstants.Roles.WRITER);

    UUID expectedId = UUID.randomUUID();
    ApplicationEntity withExpectedId = applicationEntityFactory.createDefault(builder -> builder.id(expectedId));
    when(applicationRepository.save(any())).thenReturn(withExpectedId);

    // When
    UUID entity = serviceUnderTest.createApplication(application);
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(1)).save(captor.capture());
    ApplicationEntity actualApplicationEntity = captor.getValue();
    // Then
    assertEquals(expectedId, entity);

    assertAll(() -> assertEquals(expectedUseDelegatedFunctions, actualApplicationEntity.isUseDelegatedFunctions()),
        () -> assertEquals(Instant.parse("2026-01-15T10:20:30Z"), actualApplicationEntity.getSubmittedAt()));
  }

  @Test
  public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
    // given
    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault()))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(any(UUID.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault()))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(any(UUID.class));
    verify(domainEventRepository, never()).save(any());
  }

  @ParameterizedTest
  @MethodSource("invalidApplicationRequests")
  public void GivenInvalidApplicationAndRoleWriter_whenCreateApplication_thenValidationExceptionWithCorrectMessage(
      ApplicationCreateRequest applicationCreateRequest,
      ValidationException validationException
  ) {
    setSecurityContext(TestConstants.Roles.WRITER);

    Throwable thrown = catchThrowable(() -> serviceUnderTest.createApplication(applicationCreateRequest));
    assertThat(thrown)
        .isInstanceOf(ValidationException.class)
        .usingRecursiveComparison()
        .isEqualTo(validationException);

    verify(applicationRepository, never()).findById(any(UUID.class));
    verify(applicationRepository, never()).save(any());
    verify(domainEventRepository, never()).save(any());
  }


  private Stream<Arguments> invalidApplicationRequests() {
    ApplicationContent applicationContent = applicationContentFactory.createDefault(appContentBuilder ->
        appContentBuilder.proceedings(List.of(proceedingFactory.createDefault(proceedingBuilder ->
            proceedingBuilder.leadProceeding(false)))));
    RequestApplicationContent requestApplicationContent = requestApplicationContentFactory.createDefault(
        detailsBuilder -> detailsBuilder.applicationContent(
            applicationContent));
    ValidationException validationException = new ValidationException(List.of(
        "No lead proceeding found in application content"
    ));
    ApplicationCreateRequest createRequest = applicationCreateRequestFactory.createDefault(builder -> builder
        .applicationContent(requestApplicationContent));
    return Stream.of(
        Arguments.of(
            createRequest, validationException
        ));
  }

  private RequestApplicationContent getAppContentParent(List<ProceedingJsonObject> proceedings,
                                                        String appContentId) {

    String submittedAt = "2026-01-15T10:20:30Z";
    List<Proceeding> proceedingList = proceedings.stream()
        .map(jsonObject -> {
          Proceeding base = proceedingFactory.createDefault(builder ->
              builder.id(UUID.fromString(jsonObject.id()))
                  .leadProceeding(jsonObject.leadProceeding())
                  .categoryOfLaw(jsonObject.categoryOfLaw())
                  .matterType(jsonObject.matterType())
                  .usedDelegatedFunctions(jsonObject.usedDelegatedFunctions()));
          // Ensure additionalProperties contains the fixed submittedAt value
          base.putAdditionalProperty("submittedAt", submittedAt);
          base.putAdditionalProperty("added in test", "addedValue");
          return base;
        })
        .toList();

    ApplicationContent applicationContent = applicationContentFactory.createDefault(appContentBuilder ->
        appContentBuilder.submittedAt("2026-01-15T10:20:30Z").proceedings(proceedingList).id(UUID.fromString(appContentId)));

    RequestApplicationContent requestApplicationContentDefault = requestApplicationContentFactory.createDefault();


    System.out.println("Properties from default: " + requestApplicationContentDefault.getAdditionalProperties());
    applicationContent.putAdditionalProperty("submittedAt", submittedAt);
    RequestApplicationContent RequestApplicationContent =
        requestApplicationContentFactory.createDefault(builder -> builder.applicationContent(applicationContent));
    RequestApplicationContent.putAdditionalProperty("testPropertyInTest", "testValue");
    return RequestApplicationContent;
  }

  private static ProceedingJsonObject getProceedingJsonObject(Boolean useDelegatedFunctions, boolean leadProceeding) {
    return ProceedingJsonObject.builder().id("f6e2c4e1-5d32-4c3e-9f0a-1e2b3c4d5e6f").leadProceeding(leadProceeding)
        .categoryOfLaw(CategoryOfLaw.FAMILY.name()).matterType(MatterType.SCA.name())
        .usedDelegatedFunctions(useDelegatedFunctions).build();
  }

  private Stream<Arguments> provideProceedingsForMapping() {
    //App Content Map, expected usedDelegatedFunctions
    return Stream.of(
        Arguments.of(applicationCreateRequestFactory.createDefault(
            builder -> builder.applicationContent(getAppContentParent(List.of(getProceedingJsonObject(true, true)),
                UUID.randomUUID().toString()))), true),
        Arguments.of(applicationCreateRequestFactory.createDefault(
                builder -> builder.applicationContent(getAppContentParent(List.of(getProceedingJsonObject(false, true)),
                    UUID.randomUUID().toString()))), false,
            true), Arguments.of(
            applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(
                getAppContentParent(List.of(getProceedingJsonObject(false, true), getProceedingJsonObject(true, false)),
                    UUID.randomUUID().toString()))), true,
            true), Arguments.of(
            applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(
                getAppContentParent(List.of(getProceedingJsonObject(false, true), getProceedingJsonObject(false, false)),
                    UUID.randomUUID().toString()))), false,
            true));
  }


  private void verifyThatApplicationSaved(ApplicationCreateRequest applicationCreateRequest, int timesCalled) {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(timesCalled)).save(captor.capture());
    ApplicationEntity actualApplicationEntity = captor.getValue();

    assertThat(actualApplicationEntity.getStatus()).isEqualTo(applicationCreateRequest.getStatus());
    assertThat(actualApplicationEntity.getLaaReference()).isEqualTo(applicationCreateRequest.getLaaReference());
    RequestApplicationContent applicationContentDetails =
        applicationCreateRequest.getApplicationContent();
    assertThat(actualApplicationEntity.getApplyApplicationId()).isEqualTo(
        applicationContentDetails.getApplicationContent().getId());
    assertThat(actualApplicationEntity.isUseDelegatedFunctions()).isEqualTo(
        applicationContentDetails.getApplicationContent().getProceedings().get(0)
            .getUsedDelegatedFunctions());
    assertThat(actualApplicationEntity.getApplicationContent())
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(objectMapper.convertValue(applicationContentDetails, Map.class));

    assertIndividualCollectionsEqual(applicationCreateRequest.getIndividuals(),
        actualApplicationEntity.getIndividuals());
  }


  private void verifyThatCreateDomainEventSaved(DomainEventEntity expectedDomainEvent, int timesCalled)
      throws JsonProcessingException {
    ArgumentCaptor<DomainEventEntity> captor = ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(timesCalled)).save(captor.capture());
    DomainEventEntity actualDomainEvent = captor.getValue();
    assertThat(expectedDomainEvent)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "data")
        .isEqualTo(actualDomainEvent);
    assertThat(actualDomainEvent.getCreatedAt()).isNotNull();

    Map<String, Object> expectedData = objectMapper.readValue(expectedDomainEvent.getData(), Map.class);
    Map<String, Object> actualData = objectMapper.readValue(actualDomainEvent.getData(), Map.class);
    assertThat(expectedData)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("createdDate")
        .isEqualTo(actualData);
    assertThat(actualData.get("createdDate")).isNotNull();
  }

}
