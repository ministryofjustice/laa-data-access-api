package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

import java.time.Instant;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DomainEventServiceTest {

    @InjectMocks
    private DomainEventService service;

    @Mock
    private DomainEventRepository repository;

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
                .caseWorkerId(caseworkerId)
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
                        entity.getCaseWorkerId().equals(caseworkerId) &&
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
                .caseWorkerId(caseworkerId)
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
                        entity.getCaseWorkerId().equals(caseworkerId) &&
                        entity.getType() == DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER &&
                        entity.getData().equals(jsonObject)
                ));
    }
}
