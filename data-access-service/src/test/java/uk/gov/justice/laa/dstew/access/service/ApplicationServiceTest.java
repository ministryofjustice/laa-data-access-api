package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;


@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  @InjectMocks
  private ApplicationService service;

  @Mock
  private ApplicationRepository repository;
  @Mock
  private CaseworkerRepository caseworkerRepository;
  @Mock
  private ApplicationValidations validator;
  @Mock
  private ApplicationMapper mapper;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private DomainEventService domainEventService;

//  @Test
//  void shouldThrowExceptionWhenApplicationNotFound() {
//    UUID id = UUID.randomUUID();
//    when(repository.findById(id)).thenReturn(Optional.empty());
//
//    assertThrows(ResourceNotFoundException.class, () -> service.updateApplication(id, new ApplicationUpdateRequest()));
//  }
//
//  @Test
//  void shouldCreateApplication() {
//    ApplicationCreateRequest req = new ApplicationCreateRequest();
//    req.setStatus(ApplicationStatus.IN_PROGRESS);
//
//    ApplicationEntity entity = new ApplicationEntity();
//    when(mapper.toApplicationEntity(req)).thenReturn(entity);
//    when(repository.save(entity)).thenReturn(entity);
//
//    UUID result = service.createApplication(req);
//    assertThat(result).isEqualTo(entity.getId());
//    assertThat(entity.getSchemaVersion()).isEqualTo(1);
//    verify(validator).checkApplicationCreateRequest(req);
//    verify(repository).save(entity);
//  }
//
//  @Test
//  void shouldGetAllApplications() {
//    ApplicationEntity entity = new ApplicationEntity();
//    entity.setId(UUID.randomUUID());
//    entity.setStatus(ApplicationStatus.IN_PROGRESS);
//
//    when(repository.findAll()).thenReturn(List.of(entity));
//    when(mapper.toApplication(entity)).thenReturn(new Application());
//
//    List<Application> results = service.getAllApplications();
//    assertThat(results).hasSize(1);
//  }
//
//  @Test
//  void shouldUpdateApplication() {
//    UUID id = UUID.randomUUID();
//    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
//    ApplicationEntity entity = new ApplicationEntity();
//    entity.setId(id);
//
//    when(repository.findById(id)).thenReturn(Optional.of(entity));
//
//    service.updateApplication(id, req);
//
//    verify(mapper).updateApplicationEntity(entity, req);
//    verify(repository).save(entity);
//    assertThat(entity.getModifiedAt()).isNotNull();
//  }
//
//  @Test
//  void shouldGetApplicationById() {
//    UUID id = UUID.randomUUID();
//    ApplicationEntity entity = new ApplicationEntity();
//    entity.setId(id);
//
//    when(repository.findById(id)).thenReturn(Optional.of(entity));
//    when(mapper.toApplication(entity)).thenReturn(new Application());
//
//    Application result = service.getApplication(id);
//    assertThat(result).isNotNull();
//  }
//
//  @Test
//  void shouldAssignCaseworkerToApplication() {
//    UUID appId1 = UUID.randomUUID();
//    UUID appId2 = UUID.randomUUID();
//    UUID cwId = UUID.randomUUID();
//
//    ApplicationEntity appEntity1 = new ApplicationEntity();
//    appEntity1.setId(appId1);
//
//    ApplicationEntity appEntity2 = new ApplicationEntity();
//    appEntity2.setId(appId2);
//
//    List<ApplicationEntity> applications = List.of(appEntity1, appEntity2);
//    List<UUID> applicationIds = List.of(appId1, appId2);
//
//    CaseworkerEntity caseworker = new CaseworkerEntity();
//    caseworker.setId(cwId);
//
//    EventHistory eventHistory = EventHistory.builder()
//                                .eventDescription("description")
//                                .build();
//
//    when(repository.findAllById(applicationIds)).thenReturn(applications);
//    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.of(caseworker));
//    doNothing().when(domainEventService).saveAssignApplicationDomainEvent(
//            eq(appEntity1.getId()),
//            eq(cwId),
//            eq(eventHistory.getEventDescription()));
//    doNothing().when(domainEventService).saveAssignApplicationDomainEvent(
//            eq(appEntity2.getId()),
//            eq(cwId),
//            eq(eventHistory.getEventDescription()));
//    service.assignCaseworker(cwId, List.of(appId1, appId2), eventHistory);
//
//    assertThat(appEntity1.getCaseworker()).isEqualTo(caseworker);
//    assertThat(appEntity1.getModifiedAt()).isNotNull();
//
//    assertThat(appEntity2.getCaseworker()).isEqualTo(caseworker);
//    assertThat(appEntity2.getModifiedAt()).isNotNull();
//
//    verify(domainEventService).saveAssignApplicationDomainEvent(
//            eq(appEntity1.getId()),
//            eq(cwId),
//            eq(eventHistory.getEventDescription()));
//    verify(domainEventService).saveAssignApplicationDomainEvent(
//            eq(appEntity2.getId()),
//            eq(cwId),
//            eq(eventHistory.getEventDescription()));
//    verify(repository).save(appEntity1);
//    verify(repository).save(appEntity2);
//  }
//
//  @Test
//  void shouldThrowExceptionWhenCaseworkerNotFound() {
//    UUID cwId = UUID.randomUUID();
//
//    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.empty());
//
//    assertThrows(ResourceNotFoundException.class,
//        () -> service.assignCaseworker(cwId, List.of(UUID.randomUUID()), new EventHistory()));
//  }
//
//  @Test
//  void shouldUnassignCaseworker_whenAssigned() {
//    UUID appId = UUID.randomUUID();
//    ApplicationEntity entity = new ApplicationEntity();
//    entity.setId(appId);
//
//    CaseworkerEntity cw = new CaseworkerEntity();
//    cw.setId(UUID.randomUUID());
//    entity.setCaseworker(cw);
//
//    when(repository.findById(appId)).thenReturn(Optional.of(entity));
//
//    service.unassignCaseworker(appId, new EventHistory());
//
//    assertThat(entity.getCaseworker()).isNull();
//    verify(repository).save(entity);
//  }
//
//  @Test
//  void shouldNotSave_whenAlreadyUnassigned() {
//    UUID appId = UUID.randomUUID();
//    ApplicationEntity entity = new ApplicationEntity();
//    entity.setId(appId);
//
//    // noop
//    entity.setCaseworker(null);
//
//    when(repository.findById(appId)).thenReturn(Optional.of(entity));
//
//    service.unassignCaseworker(appId, null);
//
//    verify(repository).findById(appId);
//    verify(repository, never()).save(any());
//  }
//
//  @Test
//  void shouldThrowException_whenAppNotFound() {
//    UUID appId = UUID.randomUUID();
//
//    assertThrows(ResourceNotFoundException.class,
//        () -> service.unassignCaseworker(appId, null));
//  }
//
//  @Test
//  void shouldOnlySearchForDistinctApplicationIds_whenAssigning() {
//    UUID appId1 = UUID.randomUUID();
//    UUID cwId = UUID.randomUUID();
//    EventHistory history = EventHistory.builder().eventDescription("event description").build();
//
//    ApplicationEntity appEntity1 = ApplicationEntity.builder().id(appId1).build();
//
//    List<ApplicationEntity> applications = List.of(appEntity1);
//    List<UUID> applicationIds = List.of(appId1, appId1, appId1);
//
//    CaseworkerEntity caseworker = CaseworkerEntity.builder().id(cwId).build();
//
//    when(repository.findAllById(List.of(appId1))).thenReturn(applications);
//    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.of(caseworker));
//    service.assignCaseworker(cwId, applicationIds, history);
//
//    verify(repository).findAllById(List.of(appId1));
//  }
//
//  @Test
//  void shouldThrowExceptionWhenAssignedApplicationsNotFound() {
//    UUID appId1 = UUID.randomUUID();
//    UUID appId2 = UUID.randomUUID();
//    UUID appId3 = UUID.randomUUID();
//    UUID cwId = UUID.randomUUID();
//
//    ApplicationEntity appEntity1 = new ApplicationEntity();
//    appEntity1.setId(appId1);
//
//    List<ApplicationEntity> applications = List.of(appEntity1);
//    List<UUID> applicationIds = List.of(appId1, appId2, appId3);
//
//    CaseworkerEntity caseworker = new CaseworkerEntity();
//    caseworker.setId(cwId);
//
//    when(repository.findAllById(applicationIds)).thenReturn(applications);
//    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.of(caseworker));
//    ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
//        () -> service.assignCaseworker(cwId, applicationIds, new EventHistory()));
//    assertThat(exception.getMessage()).isEqualTo("No application found with ids: "
//            + appId2 + "," + appId3);
//  }
//
//  @Test
//  void shouldAssignCaseworkerToApplicationWhenNullEventDescription() {
//    UUID applicationId = UUID.randomUUID();
//    UUID caseWorkerId = UUID.randomUUID();
//
//    ApplicationEntity applicationEntity = new ApplicationEntity();
//    applicationEntity.setId(applicationId);
//
//    CaseworkerEntity caseworker = new CaseworkerEntity();
//    caseworker.setId(caseWorkerId);
//
//    EventHistory eventHistory = EventHistory.builder()
//            .eventDescription(null)
//            .build();
//
//    when(repository.findAllById(List.of(applicationId))).thenReturn(List.of(applicationEntity));
//    when(caseworkerRepository.findById(caseWorkerId)).thenReturn(Optional.of(caseworker));
//    doNothing().when(domainEventService).saveAssignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(caseWorkerId),
//            eq(eventHistory.getEventDescription()));
//    service.assignCaseworker(caseWorkerId, List.of(applicationId), eventHistory);
//
//    assertThat(applicationEntity.getCaseworker()).isEqualTo(caseworker);
//    assertThat(applicationEntity.getModifiedAt()).isNotNull();
//
//    verify(domainEventService).saveAssignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(caseWorkerId),
//            eq(eventHistory.getEventDescription()));
//    verify(repository).save(applicationEntity);
//  }
//
//  @Test
//  void shouldNotThrow_whenApplicationOrderReturned_doesNotMatchOrderIdsGiven() {
//    ApplicationEntity appEntity1 = ApplicationEntity.builder().id(UUID.randomUUID()).build();
//    ApplicationEntity appEntity2 = ApplicationEntity.builder().id(UUID.randomUUID()).build();
//    ApplicationEntity appEntity3 = ApplicationEntity.builder().id(UUID.randomUUID()).build();
//    EventHistory history = EventHistory.builder().eventDescription("event description").build();
//    List<ApplicationEntity> applications = List.of(appEntity1, appEntity2, appEntity3);
//    List<UUID> applicationIds = applications.stream().map(ApplicationEntity::getId).toList();
//    CaseworkerEntity caseworker = CaseworkerEntity.builder().id(UUID.randomUUID()).build();
//
//    when(caseworkerRepository.findById(caseworker.getId())).thenReturn(Optional.of(caseworker));
//    when(repository.findAllById(applicationIds)).thenReturn(applications.reversed());
//
//    service.assignCaseworker(caseworker.getId(), applicationIds, history);
//  }
//
//  @Test
//  void shouldUnassignCaseworkerToApplicationWhenNullEventDescription() {
//    UUID applicationId = UUID.randomUUID();
//    UUID caseWorkerId = UUID.randomUUID();
//
//    CaseworkerEntity caseworker = new CaseworkerEntity();
//    caseworker.setId(caseWorkerId);
//
//    ApplicationEntity applicationEntity = new ApplicationEntity();
//    applicationEntity.setId(applicationId);
//    applicationEntity.setCaseworker(caseworker);
//
//    EventHistory eventHistory = EventHistory.builder()
//            .eventDescription(null)
//            .build();
//
//    when(repository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));
//    doNothing().when(domainEventService).saveUnassignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(null),
//            eq(eventHistory.getEventDescription()));
//    service.unassignCaseworker(applicationId, eventHistory);
//
//    verify(domainEventService).saveUnassignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(null),
//            eq(eventHistory.getEventDescription()));
//    verify(repository).save(applicationEntity);
//  }
//
//  @Test
//  void shouldUnassignCaseworkerToApplicationWhenBlankEventDescription() {
//    UUID applicationId = UUID.randomUUID();
//    UUID caseWorkerId = UUID.randomUUID();
//
//    CaseworkerEntity caseworker = new CaseworkerEntity();
//    caseworker.setId(caseWorkerId);
//
//    ApplicationEntity applicationEntity = new ApplicationEntity();
//    applicationEntity.setId(applicationId);
//    applicationEntity.setCaseworker(caseworker);
//
//    EventHistory eventHistory = EventHistory.builder()
//            .eventDescription("")
//            .build();
//
//    when(repository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));
//    doNothing().when(domainEventService).saveUnassignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(null),
//            eq(eventHistory.getEventDescription()));
//    service.unassignCaseworker(applicationId, eventHistory);
//
//    verify(domainEventService).saveUnassignApplicationDomainEvent(
//            eq(applicationEntity.getId()),
//            eq(null),
//            eq(eventHistory.getEventDescription()));
//    verify(repository).save(applicationEntity);
//  }
}
