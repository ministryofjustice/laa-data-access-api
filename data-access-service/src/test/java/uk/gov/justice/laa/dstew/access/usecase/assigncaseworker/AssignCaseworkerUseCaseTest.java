package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
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
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

// Security scenarios (@AllowApiCaseworker enforcement) require Spring AOP and are covered by
// the integration test controller/application/AssignCaseworkerTest.
@ExtendWith(MockitoExtension.class)
class AssignCaseworkerUseCaseTest {

  @Mock private AssignCaseworkerApplicationGateway applicationGateway;
  @Mock private AssignCaseworkerCaseworkerGateway caseworkerGateway;
  @Mock private DomainEventRepository domainEventRepository;

  private AssignCaseworkerUseCase useCase;

  @BeforeEach
  void setUp() {
    SaveDomainEventService saveDomainEventService =
        new SaveDomainEventService(
            domainEventRepository, MapperUtil.getObjectMapper(), new ServiceNameContext());
    useCase =
        new AssignCaseworkerUseCase(applicationGateway, caseworkerGateway, saveDomainEventService);
  }

  @Test
  void givenValidCommand_whenExecuted_thenAssignsAndFiresDomainEvent() {
    UUID caseworkerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(appId))
            .eventDescription("Caseworker assigned.")
            .build();
    ApplicationDomain app = ApplicationDomain.builder().id(appId).caseworkerId(null).build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of(appId))).thenReturn(List.of(app));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> saveCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(saveCaptor.capture());
    assertThat(saveCaptor.getValue().caseworkerId()).isEqualTo(caseworkerId);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER);
    assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(appId);
    assertThat(eventCaptor.getValue().getCaseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  void givenNullEventDescription_whenExecuted_thenAssignsAndFiresDomainEvent() {
    UUID caseworkerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(appId))
            .eventDescription(null)
            .build();
    ApplicationDomain app = ApplicationDomain.builder().id(appId).caseworkerId(null).build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of(appId))).thenReturn(List.of(app));

    useCase.execute(command);

    verify(applicationGateway).save(any(ApplicationDomain.class));
    verify(domainEventRepository).save(any(DomainEventEntity.class));
  }

  @Test
  void givenNullCaseworkerId_whenExecuted_thenThrowsResourceNotFoundException() {
    // caseworkerGateway.exists(null) returns false (Mockito default for boolean)
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(null)
            .applicationIds(List.of(UUID.randomUUID()))
            .build();

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command));

    verify(applicationGateway, never()).findAllByIds(anyList());
  }

  @Test
  void givenNullApplicationIdInList_whenExecuted_thenThrowsValidationException() {
    UUID caseworkerId = UUID.randomUUID();
    List<UUID> idsWithNull = new ArrayList<>();
    idsWithNull.add(UUID.randomUUID());
    idsWithNull.add(null);
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(idsWithNull)
            .build();

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(e -> assertThat(e.errors()).anyMatch(err -> err.contains("null values")));

    verify(caseworkerGateway, never()).exists(caseworkerId);
    verify(applicationGateway, never()).findAllByIds(anyList());
  }

  @Test
  void givenNonexistentCaseworker_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID caseworkerId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(UUID.randomUUID()))
            .build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(false);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining(caseworkerId.toString());

    verify(applicationGateway, never()).findAllByIds(anyList());
  }

  @Test
  void givenEmptyApplicationIds_whenExecuted_thenNoActionTaken() {
    UUID caseworkerId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of())
            .build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of())).thenReturn(List.of());

    useCase.execute(command);

    verify(applicationGateway, never()).save(any(ApplicationDomain.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenDuplicateApplicationIds_whenExecuted_thenOnlyDistinctIdsPassedToGateway() {
    UUID caseworkerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(appId, appId, appId))
            .build();
    ApplicationDomain app = ApplicationDomain.builder().id(appId).caseworkerId(null).build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of(appId))).thenReturn(List.of(app));

    useCase.execute(command);

    verify(applicationGateway).findAllByIds(eq(List.of(appId)));
    verify(applicationGateway, times(1)).save(any(ApplicationDomain.class));
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
  }

  @Test
  void
      givenApplicationAlreadyAssignedToSameCaseworker_whenExecuted_thenNoSaveButDomainEventFired() {
    UUID caseworkerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(appId))
            .build();
    // Application is already assigned to this caseworker
    ApplicationDomain app =
        ApplicationDomain.builder().id(appId).caseworkerId(caseworkerId).build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of(appId))).thenReturn(List.of(app));

    useCase.execute(command);

    verify(applicationGateway, never()).save(any(ApplicationDomain.class));
    verify(domainEventRepository).save(any(DomainEventEntity.class));
  }

  @Test
  void givenMissingApplicationIds_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID caseworkerId = UUID.randomUUID();
    UUID existingId = UUID.randomUUID();
    UUID missingId1 = UUID.randomUUID();
    UUID missingId2 = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(existingId, missingId1, missingId2))
            .build();
    ApplicationDomain existingApp =
        ApplicationDomain.builder().id(existingId).caseworkerId(null).build();
    when(caseworkerGateway.exists(caseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(anyList())).thenReturn(List.of(existingApp));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("No application found with ids:");

    verify(applicationGateway, never()).save(any(ApplicationDomain.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenApplicationWithExistingCaseworker_whenReassigned_thenSaveAndFireDomainEvent() {
    UUID originalCaseworkerId = UUID.randomUUID();
    UUID newCaseworkerId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    AssignCaseworkerCommand command =
        AssignCaseworkerCommand.builder()
            .caseworkerId(newCaseworkerId)
            .applicationIds(List.of(appId))
            .eventDescription("Reassigned caseworker.")
            .build();
    // Application currently has a different caseworker
    ApplicationDomain app =
        ApplicationDomain.builder().id(appId).caseworkerId(originalCaseworkerId).build();
    when(caseworkerGateway.exists(newCaseworkerId)).thenReturn(true);
    when(applicationGateway.findAllByIds(List.of(appId))).thenReturn(List.of(app));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> saveCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(saveCaptor.capture());
    assertThat(saveCaptor.getValue().caseworkerId()).isEqualTo(newCaseworkerId);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER);
  }
}
