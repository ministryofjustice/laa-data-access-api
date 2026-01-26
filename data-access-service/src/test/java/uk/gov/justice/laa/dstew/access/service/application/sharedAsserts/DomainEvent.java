package uk.gov.justice.laa.dstew.access.service.application.sharedAsserts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DomainEvent {

    public static void verifyThatDomainEventSaved(DomainEventRepository domainEventRepository, ObjectMapper objectMapper, DomainEventEntity expectedDomainEvent, int timesCalled) throws JsonProcessingException {
        ArgumentCaptor<DomainEventEntity> captor = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(domainEventRepository, times(timesCalled)).save(captor.capture());
        DomainEventEntity actualDomainEvent = captor.getValue();
        assertThat(expectedDomainEvent)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "data")
                .isEqualTo(actualDomainEvent);
        assertThat(actualDomainEvent.getCreatedAt()).isNotNull();

        Map<String, Object> expectedData = objectMapper.readValue(expectedDomainEvent.getData(), Map.class);
        Map<String, Object> actualData = objectMapper.readValue(actualDomainEvent.getData(), Map.class);
        assertThat(expectedData)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFields("createdAt")
                .isEqualTo(actualData);
        assertThat(actualData.get("createdAt")).isNotNull();
    }
}
