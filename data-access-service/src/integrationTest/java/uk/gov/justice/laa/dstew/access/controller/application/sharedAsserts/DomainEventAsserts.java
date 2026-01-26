package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Component
public class DomainEventAsserts {

    @Autowired
    private DomainEventRepository domainEventRepository;

    public void assertDomainEventsCreatedForApplications(
            List<ApplicationEntity> applications,
            UUID caseWorkerId,
            DomainEventType expectedDomainEventType,
            EventHistory expectedEventHistory
    ) {

        List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

        assertEquals(applications.size(), domainEvents.size());

        List<UUID> applicationIds = applications.stream()
                .map(ApplicationEntity::getId)
                .collect(Collectors.toList());

        for (DomainEventEntity domainEvent : domainEvents) {
            assertEquals(expectedDomainEventType, domainEvent.getType());
            assertTrue(applicationIds.contains(domainEvent.getApplicationId()));
            assertEquals(caseWorkerId, domainEvent.getCaseworkerId());
            if (expectedEventHistory.getEventDescription() != null) {
                assertTrue(domainEvent.getData().contains(expectedEventHistory.getEventDescription()));
            } else {
                assertFalse(domainEvent.getData().contains("eventDescription"));
            }
        }
    }

    public void assertDomainEventForApplication(
            ApplicationEntity application,
            DomainEventType expectedType
    ) throws Exception {

        List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

        DomainEventEntity event = domainEvents.get(0);

        assertThat(event.getApplicationId()).isEqualTo(application.getId());
        assertThat(event.getType()).isEqualTo(expectedType);
        assertThat(event.getCreatedAt()).isNotNull();

        // ---- JSON payload assertions ----
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(event.getData());

        assertThat(json.get("applicationId").asText())
                .isEqualTo(application.getId().toString());

        assertThat(json.get("applicationStatus").asText())
                .isEqualTo(application.getStatus().name());

        assertThat(json.get("applicationContent").asText())
                .contains("{"); // stored as stringified JSON

        if (expectedType == DomainEventType.APPLICATION_CREATED) {

            assertThat(json.has("createdDate")).isTrue();
            assertThat(json.get("createdDate").asText())
                    .isEqualTo(application.getCreatedAt().toString());

        } else if (expectedType == DomainEventType.APPLICATION_UPDATED) {

            assertThat(json.has("updatedDate")).isTrue();
            assertThat(json.get("updatedDate").asText())
                    .isEqualTo(application.getModifiedAt().toString());
        }
    }
}
