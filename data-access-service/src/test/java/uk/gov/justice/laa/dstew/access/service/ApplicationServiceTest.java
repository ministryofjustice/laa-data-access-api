package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.exception.CaseworkerNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;


@ExtendWith(MockitoExtension.class)
public class ApplicationServiceTest {

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

  @Test
  void shouldThrowExceptionWhenApplicationNotFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ApplicationNotFoundException.class, () -> service.updateApplication(id, new ApplicationUpdateRequest()));
  }

  @Test
  void shouldCreateApplication() {
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationEntity entity = new ApplicationEntity();
    when(mapper.toApplicationEntity(req)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);

    UUID result = service.createApplication(req);
    assertThat(result).isEqualTo(entity.getId());
    assertThat(entity.getSchemaVersion()).isEqualTo(1);
    verify(validator).checkApplicationCreateRequest(req);
    verify(repository).save(entity);
  }

  @Test
  void shouldGetAllApplications() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(UUID.randomUUID());
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    when(repository.findAll()).thenReturn(List.of(entity));
    when(mapper.toApplication(entity)).thenReturn(new Application());

    List<Application> results = service.getAllApplications();
    assertThat(results).hasSize(1);
  }

  @Test
  void shouldUpdateApplication() {
    UUID id = UUID.randomUUID();
    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);

    when(repository.findById(id)).thenReturn(Optional.of(entity));

    service.updateApplication(id, req);

    verify(mapper).updateApplicationEntity(entity, req);
    verify(repository).save(entity);
    assertThat(entity.getModifiedAt()).isNotNull();
  }

  @Test
  void shouldGetApplicationById() {
    UUID id = UUID.randomUUID();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);

    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(mapper.toApplication(entity)).thenReturn(new Application());

    Application result = service.getApplication(id);
    assertThat(result).isNotNull();
  }

  @Test
  void shouldAssignCaseworkerToApplication() {
    UUID appId = UUID.randomUUID();
    UUID cwId = UUID.randomUUID();

    ApplicationEntity appEntity = new ApplicationEntity();
    appEntity.setId(appId);

    CaseworkerEntity caseworker = new CaseworkerEntity();
    caseworker.setId(cwId);

    when(repository.findById(appId)).thenReturn(Optional.of(appEntity));
    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.of(caseworker));
    service.assignCaseworker(appId, cwId);

    assertThat(appEntity.getCaseworker()).isEqualTo(caseworker);
    assertThat(appEntity.getModifiedAt()).isNotNull();

    verify(repository).save(appEntity);
  }

  @Test
  void shouldThrowExceptionWhenCaseworkerNotFound() {
    UUID appId = UUID.randomUUID();
    UUID cwId = UUID.randomUUID();

    when(repository.findById(appId)).thenReturn(Optional.of(new ApplicationEntity()));
    when(caseworkerRepository.findById(cwId)).thenReturn(Optional.empty());

    assertThrows(CaseworkerNotFoundException.class,
        () -> service.assignCaseworker(appId, cwId));
  }

}
