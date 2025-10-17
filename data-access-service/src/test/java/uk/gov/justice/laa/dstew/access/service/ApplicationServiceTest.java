package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationProceedingEntity;
import uk.gov.justice.laa.dstew.access.entity.EmbeddedRecordHistoryEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationServiceTest {

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
        ApplicationEntity updateEntity = new ApplicationEntity();
        updateEntity.setId(UUID.randomUUID());

        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setIsEmergencyApplication(true);
        request.setClientId(UUID.randomUUID());

        when(repository.findById(any())).thenThrow(ApplicationNotFoundException.class);

        assertThrows(ApplicationNotFoundException.class,
                () -> classUnderTest.updateApplication(updateEntity.getId(), request));

    }

    @Test
    void shouldUpdateApplications() {

        UUID applicationId = UUID.randomUUID();

        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setIsEmergencyApplication(true);
        request.setClientId(UUID.randomUUID());

        ApplicationEntity foundEntity = new ApplicationEntity();
        foundEntity.setIsEmergencyApplication(request.getIsEmergencyApplication());
        foundEntity.setClientId(request.getClientId());
        foundEntity.setId(applicationId);

        ApplicationEntity savedEntity = new ApplicationEntity();
        savedEntity.setIsEmergencyApplication(foundEntity.getIsEmergencyApplication());
        savedEntity.setClientId(foundEntity.getClientId());
        savedEntity.setId(applicationId);

        doNothing().when(validator).checkApplicationUpdateRequest(any(), any());
        doNothing().when(mapper).updateApplicationEntity(any(ApplicationEntity.class), any(ApplicationUpdateRequest.class));
        when(repository.findById(any())).thenReturn(Optional.of(foundEntity));
        when(repository.save(any())).thenReturn(savedEntity);
        classUnderTest.updateApplication(applicationId, request);
    }

    @Test
    void shouldCreateApplication() {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setIsEmergencyApplication(true);
        request.setClientId(UUID.randomUUID());

        ApplicationEntity mappedEntity = new ApplicationEntity();
        mappedEntity.setIsEmergencyApplication(request.getIsEmergencyApplication());
        mappedEntity.setClientId(request.getClientId());

        ApplicationEntity savedEntity = new ApplicationEntity();
        savedEntity.setIsEmergencyApplication(mappedEntity.getIsEmergencyApplication());
        savedEntity.setClientId(mappedEntity.getClientId());
        savedEntity.setId(UUID.randomUUID());

        doNothing().when(validator).checkApplicationCreateRequest(any());
        when(mapper.toApplicationEntity(any())).thenReturn(mappedEntity);
        when(repository.save(any())).thenReturn(savedEntity);

        UUID result = classUnderTest.createApplication(request);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(savedEntity.getId());
    }

    @Test
    void shouldGetAllApplications() {
        ApplicationEntity firstEntity = new ApplicationEntity();
        firstEntity.setIsEmergencyApplication(true);
        firstEntity.setId(UUID.randomUUID());
        firstEntity.setProceedings(List.of(new ApplicationProceedingEntity(), new ApplicationProceedingEntity()));

        Application firstApplication = new Application();
        firstApplication.setId(firstEntity.getId());
        firstApplication.setIsEmergencyApplication(firstEntity.getIsEmergencyApplication());
        firstApplication.setProceedings(List.of(new ApplicationProceeding(), new ApplicationProceeding()));

        ApplicationEntity secondEntity = new ApplicationEntity();
        secondEntity.setIsEmergencyApplication(false);
        secondEntity.setId(UUID.randomUUID());
        secondEntity.setProceedings(List.of(new ApplicationProceedingEntity()));

        Application secondApplication = new Application();
        secondApplication.setId(secondEntity.getId());
        secondApplication.setIsEmergencyApplication(secondEntity.getIsEmergencyApplication());
        secondApplication.setProceedings(List.of(new ApplicationProceeding()));

        when(repository.findAll()).thenReturn(List.of(firstEntity, secondEntity));
        when(mapper.toApplication(firstEntity)).thenReturn(firstApplication);
        when(mapper.toApplication(secondEntity)).thenReturn(secondApplication);

        List<Application> result = classUnderTest.getAllApplications();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(firstEntity.getId());
        assertThat(result.get(0).getProceedings()).hasSize(2);
        assertThat(result.get(1).getId()).isEqualTo(secondEntity.getId());
        assertThat(result.get(1).getProceedings()).hasSize(1);

    }

    @Test
    void shouldGetApplicationById() {
        ApplicationEntity firstEntity = new ApplicationEntity();
        firstEntity.setId(UUID.randomUUID());
        firstEntity.setProceedings(List.of(new ApplicationProceedingEntity()));
        Application firstApplication = new Application();
        firstApplication.setId(firstEntity.getId());

        when(repository.findById(any())).thenReturn(Optional.of(firstEntity));
        when(mapper.toApplication(firstEntity)).thenReturn(firstApplication);

        Application result = classUnderTest.getApplication(firstEntity.getId());

        assertThat(result).isEqualTo(firstApplication);
        assertThat(result.getId()).isEqualTo(firstEntity.getId());
    }

    @Test
    void shouldThrowExceptionIfApplicationNotFound() {
        ApplicationEntity firstEntity = new ApplicationEntity();
        firstEntity.setId(UUID.randomUUID());
        firstEntity.setProceedings(List.of(new ApplicationProceedingEntity()));

        when(repository.findById(any())).thenThrow(ApplicationNotFoundException.class);

        assertThrows(ApplicationNotFoundException.class,
                () -> classUnderTest.getApplication(firstEntity.getId()));
    }

    @Test
    void shouldReturnObjectIdUsingReflection() throws Exception {
        class TestObject {
            private final UUID id = UUID.randomUUID();
        }
        TestObject testObject = new TestObject();

        // Use reflection to invoke private method getObjectId
        var method = ApplicationService.class.getDeclaredMethod("getObjectId", Object.class);
        method.setAccessible(true);
        Object id = method.invoke(classUnderTest, testObject);

        assertThat(id).isEqualTo(testObject.id);
    }

    @Test
    void shouldReturnNullObjectIdIfNoIdField() throws Exception {
        class NoIdObject {
            private String name = "test";
        }
        NoIdObject noIdObject = new NoIdObject();

        var method = ApplicationService.class.getDeclaredMethod("getObjectId", Object.class);
        method.setAccessible(true);
        Object id = method.invoke(classUnderTest, noIdObject);

        assertThat(id).isNull();
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
                "name", "updatedName",
                "count", 42,
                "nonExistent", "ignored"
        );

        method.invoke(classUnderTest, target, fields);

        assertThat(target.name).isEqualTo("updatedName");
        assertThat(target.count).isEqualTo(42);
    }

    @Test
    void shouldUpdateApplicationAndCreateSnapshot() {
        UUID applicationId = UUID.randomUUID();
        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setIsEmergencyApplication(true);
        request.setClientId(UUID.randomUUID());

        ApplicationEntity foundEntity = new ApplicationEntity();
        foundEntity.setId(applicationId);
        foundEntity.setIsEmergencyApplication(false);
        foundEntity.setClientId(UUID.randomUUID());

        doNothing().when(validator).checkApplicationUpdateRequest(any(), any());
        doNothing().when(mapper).updateApplicationEntity(any(ApplicationEntity.class), any(ApplicationUpdateRequest.class));
        when(repository.findById(any())).thenReturn(Optional.of(foundEntity));
        when(repository.save(any())).thenReturn(foundEntity);
        when(mapper.toApplication(any())).thenReturn(new Application());

        classUnderTest.updateApplication(applicationId, request);

        verify(repository).save(foundEntity);
        verify(mapper).updateApplicationEntity(foundEntity, request);
    }


}
