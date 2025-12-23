package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.anyOf;
import com.fasterxml.jackson.core.JsonProcessingException;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.UnassignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

@ExtendWith(MockitoExtension.class)
public class DomainEventServiceTest {

    @InjectMocks
    private DomainEventService service;

    @Mock
    private DomainEventRepository repository;

    @Mock
    private DomainEventMapper mapper;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void shouldSaveAssignApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();
        String jsonObject = "{\"field\":\"data\"}";

        AssignApplicationDomainEventDetails data = AssignApplicationDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseWorkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .eventDescription("description")
                .build();

        DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(jsonObject)
                .build();

        when(objectMapper.writeValueAsString(any(AssignApplicationDomainEventDetails.class)))
                .thenReturn(jsonObject);
        when(repository.save(any(DomainEventEntity.class))).thenReturn(domainEventEntity);

        service.saveAssignApplicationDomainEvent(applicationId, caseworkerId, data.getEventDescription());

        verify(objectMapper, times(1)).writeValueAsString(any(AssignApplicationDomainEventDetails.class));
        verify(repository, times(1)).save(
                argThat(entity -> entity.getApplicationId().equals(applicationId) &&
                        entity.getCaseworkerId().equals(caseworkerId) &&
                        entity.getType() == DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER &&
                        entity.getData().equals(jsonObject)
        ));
    }

    @Test
    void shouldManageExceptionThrownWhenSavingAssignApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error"){});

        assertThrows(DomainEventPublishException.class,
                () -> service.saveAssignApplicationDomainEvent(applicationId, caseworkerId, "description"));
    }

    @Test
    void shouldSaveAssignApplicationDomainEventWhenNullEventDescription() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();
        String jsonObject = "{\"field\":\"data\"}";

        AssignApplicationDomainEventDetails data = AssignApplicationDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseWorkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .eventDescription(null)
                .build();

        DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(jsonObject)
                .build();

        when(objectMapper.writeValueAsString(any(AssignApplicationDomainEventDetails.class)))
                .thenReturn(jsonObject);
        when(repository.save(any(DomainEventEntity.class))).thenReturn(domainEventEntity);

        service.saveAssignApplicationDomainEvent(applicationId, caseworkerId, data.getEventDescription());

        verify(objectMapper, times(1)).writeValueAsString(any(AssignApplicationDomainEventDetails.class));
        verify(repository, times(1)).save(
                argThat(entity -> entity.getApplicationId().equals(applicationId) &&
                        entity.getCaseworkerId().equals(caseworkerId) &&
                        entity.getType() == DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER &&
                        entity.getData().equals(jsonObject)
                ));
    }

    @Test
    void whenGetDomainEvents_ThenReturnDomainEvents() {
        UUID applicationId = UUID.randomUUID();
        DomainEventEntity entity = createEntity(applicationId);
        DomainEventEntity entity2 = createEntity(applicationId);
        Specification<DomainEventEntity> spec = DomainEventSpecification.filterApplicationId(applicationId);

        when(repository.findAll(any(Specification.class)))
        .thenReturn(List.of(entity, entity2));
        //return events out of order
        when(mapper.toDomainEvent(any()))
        .thenReturn(ApplicationDomainEvent.builder().applicationId(applicationId).createdAt(OffsetDateTime.of(2025, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC)).build())
        .thenReturn(ApplicationDomainEvent.builder().applicationId(applicationId).createdAt(OffsetDateTime.of(2024, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC)).build());

        var result = service.getEvents(applicationId, List.of());
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getApplicationId()).isEqualTo(entity.getApplicationId());
        assertThat(result.get(1).getApplicationId()).isEqualTo(entity2.getApplicationId());
        //events are returned in the correct order
        assertThat(result.get(0).getCreatedAt()).isEqualTo(OffsetDateTime.of(2024, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(result.get(1).getCreatedAt()).isEqualTo(OffsetDateTime.of(2025, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        verify(repository, times(1)).findAll(any(Specification.class));
        verify(mapper, times(1)).toDomainEvent(entity);
        verify(mapper, times(1)).toDomainEvent(entity2);
        verify(mapper, times(2)).toDomainEvent(any());
    }

    private static DomainEventEntity createEntity(UUID appId) {
        return DomainEventEntity.builder()
                         .id(UUID.randomUUID())
                         .applicationId(appId)
                         .caseworkerId(UUID.randomUUID())
                         .createdAt(Instant.now())
                         .createdBy("")
                         .data("{ \"foo\" : \"bar\"}")
                         .build();
    }

    @Test
    void shouldSaveCreateApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        String createdBy = null;
        String jsonObject = "{\"applicationId\":\"" + applicationId + "\"}";

        ApplicationEntity applicationEntity = ApplicationEntity.builder()
            .id(applicationId)
            .status(ApplicationStatus.IN_PROGRESS)
            .applicationContent(Map.of("foo", "bar"))
            .build();

        when(objectMapper.writeValueAsString(any(CreateApplicationDomainEventDetails.class)))
            .thenReturn(jsonObject);

        service.saveCreateApplicationDomainEvent(applicationEntity, createdBy);

        verify(objectMapper, times(1))
            .writeValueAsString(any(CreateApplicationDomainEventDetails.class));

        verify(repository, times(1)).save(
            argThat(entity ->
                entity.getApplicationId().equals(applicationId)
                    && entity.getCaseworkerId() == null
                    && entity.getType() == DomainEventType.APPLICATION_CREATED
                    && entity.getData().equals(jsonObject)
                    && entity.getCreatedBy() == null
                    && entity.getCreatedAt() != null
            )
        );
    }

    @Test
    void shouldSaveUnassignApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();
        String jsonObject = "{\"field\":\"data\"}";

        UnassignApplicationDomainEventDetails data = UnassignApplicationDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .eventDescription("description")
                .build();

        DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER)
                .data(jsonObject)
                .build();

        when(objectMapper.writeValueAsString(any(UnassignApplicationDomainEventDetails.class)))
                .thenReturn(jsonObject);
        when(repository.save(any(DomainEventEntity.class))).thenReturn(domainEventEntity);

        service.saveUnassignApplicationDomainEvent(applicationId, caseworkerId, data.getEventDescription());

        verify(objectMapper, times(1)).writeValueAsString(any(UnassignApplicationDomainEventDetails.class));
        verify(repository, times(1)).save(
                argThat(entity -> entity.getApplicationId().equals(applicationId) &&
                        entity.getCaseworkerId().equals(caseworkerId) &&
                        entity.getType() == DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER &&
                        entity.getData().equals(jsonObject)
                ));
    }

    @Test
    void shouldManageExceptionThrownWhenSavingUnassignApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error"){});

        assertThrows(DomainEventPublishException.class,
                () -> service.saveUnassignApplicationDomainEvent(applicationId, caseworkerId, "description"));
    }

    @Test
    void shouldSaveUnassignApplicationDomainEventWhenNullEventDescription() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();
        String jsonObject = "{\"field\":\"data\"}";

        UnassignApplicationDomainEventDetails data = UnassignApplicationDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .eventDescription(null)
                .build();

        DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER)
                .data(jsonObject)
                .build();

        when(objectMapper.writeValueAsString(any(UnassignApplicationDomainEventDetails.class)))
                .thenReturn(jsonObject);
        when(repository.save(any(DomainEventEntity.class))).thenReturn(domainEventEntity);

        service.saveUnassignApplicationDomainEvent(applicationId, caseworkerId, data.getEventDescription());

        verify(objectMapper, times(1)).writeValueAsString(any(UnassignApplicationDomainEventDetails.class));
        verify(repository, times(1)).save(
                argThat(entity -> entity.getApplicationId().equals(applicationId) &&
                        entity.getCaseworkerId().equals(caseworkerId) &&
                        entity.getType() == DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER &&
                        entity.getData().equals(jsonObject)
                ));
    }

    @Test
    void shouldSaveCreateApplicationDomainEvent() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        String createdBy = null;
        String jsonObject = "{\"applicationId\":\"" + applicationId + "\"}";

        ApplicationEntity applicationEntity = ApplicationEntity.builder()
            .id(applicationId)
            .status(ApplicationStatus.IN_PROGRESS)
            .build();

        when(objectMapper.writeValueAsString(any(CreateApplicationDomainEventDetails.class)))
            .thenReturn(jsonObject);

        service.saveCreateApplicationDomainEvent(applicationEntity, createdBy);

        verify(objectMapper, times(1))
            .writeValueAsString(any(CreateApplicationDomainEventDetails.class));

        verify(repository, times(1)).save(
            argThat(entity ->
                entity.getApplicationId().equals(applicationId)
                    && entity.getCaseworkerId() == null
                    && entity.getType() == DomainEventType.APPLICATION_CREATED
                    && entity.getData().equals(jsonObject)
                    && entity.getCreatedBy() == null
                    && entity.getCreatedAt() != null
            )
        );
    }
}
