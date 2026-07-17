package uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.infrastructure.UnassignCaseworkerApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.unassigncaseworker.UnassignCaseworkerCommandGenerator;

@ExtendWith(MockitoExtension.class)
class UnassignCaseworkerUseCaseTest {

  @Mock private UnassignCaseworkerApplicationGateway applicationGateway;
  @Mock private DomainEventRepository domainEventRepository;
  @Mock private ServiceNameContext serviceNameContext;

  private UnassignCaseworkerUseCase useCase;

  @BeforeEach
  void setUp() {
    // Constructor parameters derived from UnassignCaseworkerUseCase field list:
    // applicationGateway, saveDomainEventService
    SaveDomainEventService saveDomainEventService =
        new SaveDomainEventService(
            domainEventRepository, MapperUtil.getObjectMapper(), serviceNameContext);
    useCase = new UnassignCaseworkerUseCase(applicationGateway, saveDomainEventService);
  }

  @Test
  void givenAssignedCaseworker_whenExecuted_thenUnassignsAndFiresDomainEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class,
            builder -> builder.applicationId(applicationId).eventDescription("Unassigned"));
    ApplicationDomain domain =
        ApplicationDomain.builder().id(applicationId).caseworkerId(caseworkerId).build();
    when(applicationGateway.findApplicationById(applicationId)).thenReturn(Optional.of(domain));

    useCase.execute(command);

    verify(applicationGateway, times(1)).saveApplication(domain);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(1)).save(eventCaptor.capture());
    DomainEventEntity savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedEvent.getCaseworkerId()).isNull();
    assertThat(savedEvent.getType()).isEqualTo(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER);
  }

  @Test
  void givenNullEventDescription_whenExecuted_thenUnassignsAndFiresDomainEventWithNullDescription()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class,
            builder -> builder.applicationId(applicationId).eventDescription(null));
    ApplicationDomain domain =
        ApplicationDomain.builder().id(applicationId).caseworkerId(caseworkerId).build();
    when(applicationGateway.findApplicationById(applicationId)).thenReturn(Optional.of(domain));

    useCase.execute(command);

    verify(applicationGateway, times(1)).saveApplication(domain);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(1)).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(eventCaptor.getValue().getCaseworkerId()).isNull();
    assertThat(eventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER);
  }

  @Test
  void givenEmptyEventDescription_whenExecuted_thenUnassignsAndFiresDomainEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class,
            builder -> builder.applicationId(applicationId).eventDescription(""));
    ApplicationDomain domain =
        ApplicationDomain.builder().id(applicationId).caseworkerId(caseworkerId).build();
    when(applicationGateway.findApplicationById(applicationId)).thenReturn(Optional.of(domain));

    useCase.execute(command);

    verify(applicationGateway, times(1)).saveApplication(domain);
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
  }

  @Test
  void givenAlreadyUnassigned_whenExecuted_thenNoSaveAndNoDomainEvent() {
    UUID applicationId = UUID.randomUUID();
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class,
            builder -> builder.applicationId(applicationId));
    ApplicationDomain domain =
        ApplicationDomain.builder().id(applicationId).caseworkerId(null).build();
    when(applicationGateway.findApplicationById(applicationId)).thenReturn(Optional.of(domain));

    useCase.execute(command);

    verify(applicationGateway, never()).saveApplication(any(ApplicationDomain.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenNonexistentApplication_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class,
            builder -> builder.applicationId(applicationId));
    when(applicationGateway.findApplicationById(applicationId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessage("No application found with id: " + applicationId);

    verify(applicationGateway, never()).saveApplication(any(ApplicationDomain.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenNullApplicationId_whenExecuted_thenThrowsResourceNotFoundException() {
    UnassignCaseworkerCommand command =
        DataGenerator.createDefault(
            UnassignCaseworkerCommandGenerator.class, builder -> builder.applicationId(null));
    when(applicationGateway.findApplicationById(null)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessage("No application found with id: null");

    verify(applicationGateway, never()).saveApplication(any(ApplicationDomain.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }
}
