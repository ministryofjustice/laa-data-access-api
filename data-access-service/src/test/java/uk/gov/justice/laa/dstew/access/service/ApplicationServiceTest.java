package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  @InjectMocks
  private ApplicationService classUnderTest;

  @Mock
  private ApplicationRepository repository;

  @Mock
  private ApplicationMapper mapper;

  @Mock
  private ApplicationValidations validator;

  @Mock
  private ObjectMapper objectMapper;

  @Test
  void shouldThrowExceptionWhenApplicationNotFound() {
    UUID id = UUID.randomUUID();
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    when(repository.findById(any())).thenThrow(ApplicationNotFoundException.class);

    assertThrows(ApplicationNotFoundException.class,
        () -> classUnderTest.updateApplication(id, request));
  }

  @Test
  void shouldUpdateApplicationSuccessfully() {
    UUID id = UUID.randomUUID();
    ApplicationEntity existing = new ApplicationEntity();
    existing.setId(id);
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();

    doNothing().when(validator).checkApplicationUpdateRequest(any(), any());
    when(repository.findById(any())).thenReturn(Optional.of(existing));
    when(repository.save(any())).thenReturn(existing);

    classUnderTest.updateApplication(id, request);

    verify(mapper).updateApplicationEntity(existing, request);
    verify(repository).save(existing);
  }

  @Test
  void shouldCreateApplicationSuccessfully() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    ApplicationEntity mapped = new ApplicationEntity();
    ApplicationEntity saved = new ApplicationEntity();
    saved.setId(UUID.randomUUID());

    doNothing().when(validator).checkApplicationCreateRequest(any());
    when(mapper.toApplicationEntity(request)).thenReturn(mapped);
    when(repository.save(mapped)).thenReturn(saved);

    UUID result = classUnderTest.createApplication(request);

    assertThat(result).isEqualTo(saved.getId());
    verify(repository).save(mapped);
  }

  @Test
  void shouldGetAllApplicationsSuccessfully() {
    ApplicationEntity entity1 = new ApplicationEntity();
    entity1.setId(UUID.randomUUID());
    ApplicationEntity entity2 = new ApplicationEntity();
    entity2.setId(UUID.randomUUID());

    Application app1 = new Application();
    app1.setId(entity1.getId());
    Application app2 = new Application();
    app2.setId(entity2.getId());

    when(repository.findAll()).thenReturn(List.of(entity1, entity2));
    when(mapper.toApplication(entity1)).thenReturn(app1);
    when(mapper.toApplication(entity2)).thenReturn(app2);

    List<Application> result = classUnderTest.getAllApplications();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(entity1.getId());
    assertThat(result.get(1).getId()).isEqualTo(entity2.getId());
  }

  @Test
  void shouldGetApplicationByIdSuccessfully() {
    UUID id = UUID.randomUUID();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);
    Application mapped = new Application();
    mapped.setId(id);

    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(mapper.toApplication(entity)).thenReturn(mapped);

    Application result = classUnderTest.getApplication(id);

    assertThat(result.getId()).isEqualTo(id);
  }

  @Test
  void shouldThrowExceptionIfApplicationNotFoundById() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenThrow(ApplicationNotFoundException.class);

    assertThrows(ApplicationNotFoundException.class,
        () -> classUnderTest.getApplication(id));
  }

  @Test
  void shouldPopulateFieldsOnObject() throws Exception {
    class Target {
      private String name;
      private Integer count;
    }

    Target target = new Target();

    var method = ApplicationService.class.getDeclaredMethod("populateFields", Object.class, Map.class);
    method.setAccessible(true);

    Map<String, Object> fields = Map.of(
        "name", "UpdatedName",
        "count", 10,
        "ignored", "value"
    );

    method.invoke(classUnderTest, target, fields);

    assertThat(target.name).isEqualTo("UpdatedName");
    assertThat(target.count).isEqualTo(10);
  }

  @Test
  void shouldUpdateApplicationAndCreateSnapshot() {
    UUID id = UUID.randomUUID();
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();

    ApplicationEntity existing = new ApplicationEntity();
    existing.setId(id);

    doNothing().when(validator).checkApplicationUpdateRequest(any(), any());
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    when(mapper.toApplication(existing)).thenReturn(new Application());

    classUnderTest.updateApplication(id, request);

    verify(mapper).updateApplicationEntity(existing, request);
    verify(repository).save(existing);
  }

}
