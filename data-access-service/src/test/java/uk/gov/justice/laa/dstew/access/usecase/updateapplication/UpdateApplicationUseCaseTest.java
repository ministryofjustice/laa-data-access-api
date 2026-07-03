package uk.gov.justice.laa.dstew.access.usecase.updateapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.updateapplication.UpdateApplicationCommandGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class UpdateApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private DomainEventRepository domainEventRepository;

  private UpdateApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    SaveDomainEventService saveDomainEventService =
        new SaveDomainEventService(
            domainEventRepository,
            uk.gov.justice.laa.dstew.access.mapper.MapperUtil.getObjectMapper(),
            new ServiceNameContext());
    useCase = new UpdateApplicationUseCase(applicationGateway, saveDomainEventService);
  }

  @Test
  void givenNoApplication_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class, builder -> builder.id(applicationId));

    when(applicationGateway.update(any(UUID.class), any(), any(Map.class)))
        .thenThrow(new ResourceNotFoundException("No application found with id: " + applicationId));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(updateApplicationCommand))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(applicationGateway, times(1)).update(any(UUID.class), any(), any(Map.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  void givenNullApplicationContent_whenExecuted_thenThrowsValidationException() {
    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class, builder -> builder.applicationContent(null));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(updateApplicationCommand))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Application content cannot be null")));

    verify(applicationGateway, never()).update(any(UUID.class), any(), any(Map.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  void givenEmptyApplicationContent_whenExecuted_thenThrowsValidationException() {
    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class,
            builder -> builder.applicationContent(new HashMap<>()));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(updateApplicationCommand))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Application content cannot be empty")));

    verify(applicationGateway, never()).update(any(UUID.class), any(), any(Map.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  void givenInvalidStatus_whenExecuted_thenThrowsValidationException() {
    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class, builder -> builder.status("INVALID_STATUS"));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(updateApplicationCommand))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("Invalid application status: INVALID_STATUS")));

    verify(applicationGateway, never()).update(any(UUID.class), any(), any(Map.class));
    verify(domainEventRepository, never()).save(any());
  }

  @Test
  void givenValidCommand_whenExecuted_thenReturnsSavedDomainAndPublishesDomainEvent() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> changedContent = new HashMap<>(Map.of("test", "changed"));

    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class,
            builder ->
                builder
                    .id(applicationId)
                    .status("APPLICATION_SUBMITTED")
                    .applicationContent(changedContent));

    ApplicationDomain savedDomain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            builder ->
                builder
                    .id(applicationId)
                    .status("APPLICATION_SUBMITTED")
                    .applicationContent(changedContent));

    when(applicationGateway.update(any(UUID.class), any(), any(Map.class))).thenReturn(savedDomain);

    useCase.execute(updateApplicationCommand);

    ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> contentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(applicationGateway, times(1))
        .update(idCaptor.capture(), statusCaptor.capture(), contentCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(applicationId);
    assertThat(statusCaptor.getValue()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(contentCaptor.getValue()).usingRecursiveComparison().isEqualTo(changedContent);

    ArgumentCaptor<DomainEventEntity> domainEventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(1)).save(domainEventCaptor.capture());
    assertThat(domainEventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.APPLICATION_UPDATED);
    assertThat(domainEventCaptor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(domainEventCaptor.getValue().getCaseworkerId())
        .isEqualTo(savedDomain.caseworkerId());
  }

  @Test
  void givenNullStatusField_whenExecuted_thenKeepsExistingStatus() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> changedContent = new HashMap<>(Map.of("test", "changed"));

    UpdateApplicationCommand updateApplicationCommand =
        DataGenerator.createDefault(
            UpdateApplicationCommandGenerator.class,
            builder -> builder.id(applicationId).status(null).applicationContent(changedContent));

    ApplicationDomain savedDomain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            builder ->
                builder
                    .id(applicationId)
                    .status("APPLICATION_IN_PROGRESS")
                    .applicationContent(changedContent));

    when(applicationGateway.update(any(UUID.class), any(), any(Map.class))).thenReturn(savedDomain);

    useCase.execute(updateApplicationCommand);

    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> contentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(applicationGateway)
        .update(any(UUID.class), statusCaptor.capture(), contentCaptor.capture());
    assertThat(statusCaptor.getValue()).isNull();
    assertThat(contentCaptor.getValue()).usingRecursiveComparison().isEqualTo(changedContent);
  }
}
