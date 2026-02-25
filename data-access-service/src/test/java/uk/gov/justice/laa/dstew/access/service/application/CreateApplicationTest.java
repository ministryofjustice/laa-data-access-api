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
import java.util.ArrayList;
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
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationsGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateApplicationTest extends BaseServiceTest {

  private final LinkedApplicationsGenerator linkedApplicationsGenerator = new LinkedApplicationsGenerator();
  private final ApplicationContentGenerator applicationContentGenerator = new ApplicationContentGenerator();
  private final ApplicationEntityGenerator applicationEntityGenerator = new ApplicationEntityGenerator();
  @Autowired
  private ApplicationService serviceUnderTest;

  @Test
  public void givenNewApplication_whenCreateApplication_thenReturnNewId() throws JsonProcessingException {

    // given
    UUID expectedId = UUID.randomUUID();
    ApplicationEntity withExpectedId = applicationEntityFactory.createDefault(builder ->
        builder.id(expectedId).isAutoGranted(null)
    );

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault();
    ApplicationContent applicationContent = MapperUtil.getObjectMapper()
        .convertValue(applicationCreateRequest.getApplicationContent(), ApplicationContent.class);
    when(applicationRepository.save(any())).thenReturn(withExpectedId);

    DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(expectedId)
        .type(DomainEventType.APPLICATION_CREATED)
        .data(objectMapper.writeValueAsString(CreateApplicationDomainEventDetails.builder()
            .applicationId(expectedId)
            .laaReference(withExpectedId.getLaaReference())
            .applicationStatus(ApplicationStatus.APPLICATION_IN_PROGRESS.toString())
            .request(objectMapper.writeValueAsString(applicationCreateRequest))
            .build()))
        .build();

    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    UUID actualId = serviceUnderTest.createApplication(applicationCreateRequest, 1);

    // then
    assertEquals(expectedId, actualId);

    verifyThatApplicationSaved(applicationCreateRequest, 1);
    verifyThatProceedingsSaved(applicationContent, expectedId);
    verifyThatCreateDomainEventSaved(expectedDomainEvent, 1);
  }

  @Test
  public void givenNewApplicationWithLinkedApplication_whenCreateApplication_thenReturnNewId() throws JsonProcessingException {

    // given
    UUID expectedId = UUID.randomUUID();
    UUID applyApplicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();

    ApplicationContent applicationContent = applicationContentGenerator.createDefault(appContentBuilder ->
        appContentBuilder.id(associatedApplicationId)
            .allLinkedApplications(createLinkedApplications(applyApplicationId, List.of(associatedApplicationId))));

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault(builder ->
        builder.applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    ApplicationEntity withExpectedId = applicationEntityGenerator.createDefault(builder ->
        builder.id(expectedId).applicationContent(objectMapper.convertValue(applicationContent, Map.class)).isAutoGranted(null)
    );
    ApplicationEntity leadApplication = applicationEntityGenerator
        .createDefault(builder -> builder.applyApplicationId(applyApplicationId));
    when(applicationRepository.findByApplyApplicationId(applyApplicationId))
        .thenReturn(leadApplication);
    when(applicationRepository.save(any())).thenReturn(withExpectedId);
    DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(expectedId)
        .type(DomainEventType.APPLICATION_CREATED)
        .data(objectMapper.writeValueAsString(CreateApplicationDomainEventDetails.builder()
            .applicationId(expectedId)
            .laaReference(withExpectedId.getLaaReference())
            .applicationStatus(ApplicationStatus.APPLICATION_IN_PROGRESS.toString())
            .request(objectMapper.writeValueAsString(applicationCreateRequest))
            .build()))
        .build();

    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    UUID actualId = serviceUnderTest.createApplication(applicationCreateRequest, 1);

    // then
    assertEquals(expectedId, actualId);

    verifyThatApplicationSaved(applicationCreateRequest, 2);
    verifyThatProceedingsSaved(applicationContent, expectedId);
    verifyThatCreateDomainEventSaved(expectedDomainEvent, 1);
  }

  @Test
  public void givenNewApplicationWithLinkedApplication_throwExceptionWhenMissingLeadApplication()
      throws JsonProcessingException {

    // given
    UUID expectedId = UUID.randomUUID();
    UUID applyApplicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();

    ApplicationContent applicationContent = applicationContentGenerator.createDefault(appContentBuilder ->
        appContentBuilder.id(associatedApplicationId)
            .allLinkedApplications(createLinkedApplications(applyApplicationId, List.of(associatedApplicationId))));

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault(builder ->
        builder.applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    ApplicationEntity withExpectedId = applicationEntityGenerator.createDefault(builder ->
        builder.id(expectedId).applicationContent(objectMapper.convertValue(applicationContent, Map.class)).isAutoGranted(null)
    );
    when(applicationRepository.findByApplyApplicationId(applyApplicationId))
        .thenReturn(null);
    when(applicationRepository.save(any())).thenReturn(withExpectedId);
    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequest))
        .withMessageContaining("Linking failed > Lead application not found, ID: " + applyApplicationId);
  }

  @Test
  public void givenNewApplicationWithLinkedApplication_throwExceptionWhenMissingAssociatedApplication()
      throws JsonProcessingException {

    // given
    UUID expectedId = UUID.randomUUID();
    UUID applyApplicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();
    UUID otherAssociatedApplication = UUID.randomUUID();

    List<LinkedApplication> linkedApplications =
        createLinkedApplications(applyApplicationId, List.of(associatedApplicationId, otherAssociatedApplication));

    ApplicationContent applicationContent = applicationContentGenerator.createDefault(appContentBuilder ->
        appContentBuilder.id(associatedApplicationId).allLinkedApplications(linkedApplications));

    ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault(builder ->
        builder.applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    ApplicationEntity withExpectedId = applicationEntityGenerator.createDefault(builder ->
        builder.id(expectedId).applicationContent(objectMapper.convertValue(applicationContent, Map.class)).isAutoGranted(null)
    );

    ApplicationEntity leadApplication = applicationEntityGenerator
        .createDefault(builder -> builder.applyApplicationId(applyApplicationId));
    when(applicationRepository.findByApplyApplicationId(applyApplicationId))
        .thenReturn(leadApplication);

    when(applicationRepository.findByApplyApplicationId(otherAssociatedApplication))
        .thenReturn(null);
    when(applicationRepository.save(any())).thenReturn(withExpectedId);
    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequest))
        .withMessageContaining("No linked application found with associated apply ids: " + List.of(otherAssociatedApplication));
  }

  private List<LinkedApplication> createLinkedApplications(UUID leadApplicationId, List<UUID> associatedApplicationIds) {
    List<LinkedApplication> linkedApplications = new ArrayList<>();
    for (UUID associatedApplicationId : associatedApplicationIds) {
      linkedApplications.add(linkedApplicationsGenerator.createDefault(
          builder -> builder.leadApplicationId(leadApplicationId).associatedApplicationId(associatedApplicationId)));
    }
    return linkedApplications;
  }

  private void verifyThatProceedingsSaved(ApplicationContent applicationCreateRequest, UUID expectedId) {
    ArgumentCaptor<List<ProceedingEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
    verify(proceedingRepository).saveAll(captor.capture());
    List<ProceedingEntity> actualProceedingEntities = captor.getValue();

    ApplicationContent applicationContentDetails =
        objectMapper.convertValue(applicationCreateRequest, ApplicationContent.class);

    List<Proceeding> expectedProceedings = applicationContentDetails.getProceedings();

    assertEquals(expectedProceedings.size(), actualProceedingEntities.size());
    for (int index = 0; index < expectedProceedings.size(); index++) {
      Proceeding expectedProceeding = expectedProceedings.get(index);
      ProceedingEntity actualProceedingEntity = actualProceedingEntities.get(index);

      assertThat(actualProceedingEntity.getApplicationId()).isEqualTo(expectedId);
      assertThat(actualProceedingEntity.isLead()).isEqualTo(expectedProceeding.getLeadProceeding());
      assertThat(actualProceedingEntity.getProceedingContent()).isEqualTo(
          objectMapper.convertValue(expectedProceeding, Map.class));
    }
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
    UUID entity = serviceUnderTest.createApplication(application, 1);
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(1)).save(captor.capture());
    ApplicationEntity actualApplicationEntity = captor.getValue();
    // Then
    assertEquals(expectedId, entity);

    assertAll(() -> assertEquals(expectedUseDelegatedFunctions, actualApplicationEntity.getUsedDelegatedFunctions()),
        () -> assertEquals(Instant.parse("2026-01-15T10:20:30Z"), actualApplicationEntity.getSubmittedAt()));
    verifyThatProceedingsSaved(
        objectMapper.convertValue(application.getApplicationContent(), ApplicationContent.class),
        expectedId);
  }

  @Test
  public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
    // given
    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault(), 1))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(any(UUID.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault(), 1))
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

    Throwable thrown = catchThrowable(() -> serviceUnderTest.createApplication(applicationCreateRequest, 1));
    assertThat(thrown)
        .isInstanceOf(ValidationException.class)
        .usingRecursiveComparison()
        .isEqualTo(validationException);

    verify(applicationRepository, never()).findById(any(UUID.class));
    verify(applicationRepository, never()).save(any());
    verify(domainEventRepository, never()).save(any());
  }


  private Stream<Arguments> invalidApplicationRequests() {
    ValidationException validationException = new ValidationException(List.of(
        "No lead proceeding found in application content"
    ));

    ApplicationContent applicationContent = applicationContentFactory.createDefault(appContentBuilder ->
        appContentBuilder.proceedings(List.of(proceedingFactory.createDefault(proceedingBuilder ->
            proceedingBuilder.leadProceeding(false)))));
    ApplicationCreateRequest createRequest = applicationCreateRequestFactory.createDefault(builder -> builder
        .applicationContent(objectMapper.convertValue(applicationContent, Map.class)));
    return Stream.of(
        Arguments.of(
            createRequest, validationException
        ));
  }

  private Map<String, Object> getAppContentParent(List<Proceeding> proceedings,
                                                  String appContentId) {


    ApplicationContent applicationContent = applicationContentFactory.createDefault(appContentBuilder ->
        appContentBuilder.submittedAt("2026-01-15T10:20:30Z").proceedings(proceedings).id(UUID.fromString(appContentId)));

    applicationContent.putAdditionalApplicationContent("testPropertyInTest", "testValue");
    return objectMapper.convertValue(applicationContent, Map.class);

  }

  private Proceeding getProceeding(Boolean useDelegatedFunctions, boolean leadProceeding) {

    return proceedingFactory.createDefault(
        builder -> builder.leadProceeding(leadProceeding).usedDelegatedFunctions(useDelegatedFunctions));
  }

  private Stream<Arguments> provideProceedingsForMapping() {
    //App Content Map, expected usedDelegatedFunctions
    return Stream.of(
        Arguments.of(applicationCreateRequestFactory.createDefault(
            builder -> builder.applicationContent(getAppContentParent(List.of(getProceeding(true, true)),
                UUID.randomUUID().toString()))), true),
        Arguments.of(applicationCreateRequestFactory.createDefault(
                builder -> builder.applicationContent(getAppContentParent(List.of(getProceeding(false, true)),
                    UUID.randomUUID().toString()))), false,
            true), Arguments.of(
            applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(
                getAppContentParent(List.of(getProceeding(false, true), getProceeding(true, false)),
                    UUID.randomUUID().toString()))), true,
            true), Arguments.of(
            applicationCreateRequestFactory.createDefault(builder -> builder.applicationContent(
                getAppContentParent(List.of(getProceeding(false, true), getProceeding(false, false)),
                    UUID.randomUUID().toString()))), false,
            true));
  }


  private void verifyThatApplicationSaved(ApplicationCreateRequest applicationCreateRequest, int timesCalled) {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(timesCalled)).save(captor.capture());
    List<ApplicationEntity> capturedEntities = captor.getAllValues();
    //ignore second saves that might happen due to linked applications for this assert
    ApplicationEntity actualApplicationEntity = capturedEntities.getFirst();

    assertThat(actualApplicationEntity.getStatus()).isEqualTo(applicationCreateRequest.getStatus());
    assertThat(actualApplicationEntity.getLaaReference()).isEqualTo(applicationCreateRequest.getLaaReference());
    ApplicationContent applicationContentDetails =
        objectMapper.convertValue(applicationCreateRequest.getApplicationContent(), ApplicationContent.class);
    assertThat(actualApplicationEntity.getApplyApplicationId()).isEqualTo(
        applicationContentDetails.getId());
    assertThat(actualApplicationEntity.getUsedDelegatedFunctions()).isEqualTo(
        applicationContentDetails.getProceedings().getFirst()
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
