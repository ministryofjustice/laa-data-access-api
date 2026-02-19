package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.model.S3UploadResult;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;
import uk.gov.justice.laa.dstew.access.service.S3Service;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UpdateApplicationTest extends BaseServiceTest {
  @MockitoBean
  private S3Service s3Service;

  @MockitoBean
  private DynamoDbService dynamoDbService;

  @Autowired
  private ApplicationService serviceUnderTest;

//    @MockitoBean EventHistoryPublisher eventHistoryPublisher;

  @Test
  void givenNoApplication_whenUpdateApplication_thenThrowResourceNotFoundException() {
    // given
    UUID applicationId = UUID.randomUUID();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());
    setSecurityContext(TestConstants.Roles.WRITER);

    // when / then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
        .withMessageContaining("No application found with id: " + applicationId);
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  void givenApplication_whenUpdateApplication_thenUpdateAndSave() throws JsonProcessingException {
    // given
    UUID applicationId = UUID.randomUUID();
    ApplicationEntity expectedEntity = applicationEntityFactory.createDefault(builder ->
        builder.id(applicationId)
            .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
    );
    ApplicationEntity updatedEntity = expectedEntity.toBuilder()
        .applicationContent(new HashMap<>(Map.of("test", "changed")))
        .build();

    ApplicationUpdateRequest updateRequest = applicationUpdateRequestFactory.createDefault();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedEntity));
    when(s3Service.upload(any(), any(String.class), any(String.class)))
        .thenReturn(new S3UploadResult("bucket", "key", "etag", true, "s3://bucket/key"));
    when(dynamoDbService.saveDomainEvent(any(Event.class), any(String.class))).thenReturn(null);
    setSecurityContext(TestConstants.Roles.WRITER);

    DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(applicationId)
        .type(DomainEventType.APPLICATION_UPDATED)
        .createdBy("")
        .data(objectMapper.writeValueAsString(UpdateApplicationDomainEventDetails.builder()
            .applicationId(applicationId)
            .applicationStatus(ApplicationStatus.APPLICATION_IN_PROGRESS.toString())
            .applicationContent(updatedEntity.getApplicationContent().toString())
            .build()))
        .build();

    // when
    serviceUnderTest.updateApplication(applicationId, updateRequest);

    // then
    verify(applicationRepository, times(1)).findById(applicationId);
//        verify(eventHistoryPublisher, times(1)).processEventAsync(any(Event.class));
    verifyThatApplicationUpdated(updateRequest, 1);
    verifyThatUpdateDomainEventSaved(expectedDomainEvent, 1);
    assertThat(expectedEntity.getModifiedAt()).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("invalidApplicationUpdateRequests")
  void givenApplicationAndInvalidUpdateRequest_whenUpdateApplication_thenValidationExceptionWithCorrectMessage(
      UUID applicationId,
      ApplicationUpdateRequest applicationUpdateRequest,
      ValidationException validationException
  ) {
    // given
    ApplicationEntity expectedEntity = applicationEntityFactory.createDefault(builder ->
        builder.id(applicationId)
            .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
    );
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedEntity));

    setSecurityContext(TestConstants.Roles.WRITER);

    // when
    // then
    Throwable thrown = catchThrowable(() -> serviceUnderTest.updateApplication(applicationId, applicationUpdateRequest));
    assertThat(thrown)
        .isInstanceOf(ValidationException.class)
        .usingRecursiveComparison()
        .isEqualTo(validationException);
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(applicationRepository, never()).save(any());
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  public void givenApplicationUpdateAndNotRoleWriter_whenCreateApplication_thenThrowUnauthorizedException() {
    // given
    UUID applicationId = UUID.randomUUID();

    setSecurityContext(TestConstants.Roles.READER);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
        .withMessageContaining("Access Denied");
    verify(applicationRepository, never()).findById(applicationId);
    verify(applicationRepository, never()).save(any(ApplicationEntity.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  public void givenApplicationUpdateAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {
    // given
    UUID applicationId = UUID.randomUUID();

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
        .withMessageContaining("Access Denied");
    verify(applicationRepository, never()).findById(applicationId);
    verify(applicationRepository, never()).save(any(ApplicationEntity.class));
    verify(domainEventRepository, never()).save(any());
  }

  public final Stream<Arguments> invalidApplicationUpdateRequests() {
    return Stream.of(
        Arguments.of(UUID.randomUUID(),
            applicationUpdateRequestFactory.createDefault(builder -> builder
                .applicationContent(new HashMap<>())),
            new ValidationException(List.of(
                "Application content cannot be empty"
            ))
        )
    );
  }

  private void verifyThatApplicationUpdated(ApplicationUpdateRequest applicationUpdateRequest, int timesCalled) {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(timesCalled)).save(captor.capture());
    ApplicationEntity actualApplicationEntity = captor.getValue();

    assertThat(actualApplicationEntity.getStatus()).isEqualTo(applicationUpdateRequest.getStatus());
    assertThat(actualApplicationEntity.getApplicationContent())
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(applicationUpdateRequest.getApplicationContent());
  }

  private void verifyThatUpdateDomainEventSaved(DomainEventEntity expectedDomainEvent, int timesCalled)
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
        .ignoringFields("updatedDate")
        .isEqualTo(actualData);
    assertThat(actualData.get("updatedDate")).isNotNull();
  }
}
