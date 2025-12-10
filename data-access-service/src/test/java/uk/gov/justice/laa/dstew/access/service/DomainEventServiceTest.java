package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

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
    void shouldSaveAssignApplicationDomainEvent() {
        UUID appId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();

        service.saveAssignApplicationDomainEvent(appId, caseworkerId, "description");
        verify(repository).save(any());
    }

    @Test
    void shouldManageExceptionThrownWhenSavingAssignApplicationDomainEvent() throws JsonProcessingException {
        UUID appId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error"){});

        assertThrows(DomainEventPublishException.class,
                () -> service.saveAssignApplicationDomainEvent(appId, caseworkerId, "description"));
    }
}
