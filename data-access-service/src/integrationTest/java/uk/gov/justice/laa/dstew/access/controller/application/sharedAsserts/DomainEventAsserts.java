package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

@Component
public class DomainEventAsserts {

  @Autowired private DomainEventRepository domainEventRepository;

  public void assertDomainEventsCreatedForApplications(
      List<ApplicationEntity> applications,
      UUID caseWorkerId,
      DomainEventType expectedDomainEventType,
      EventHistoryRequest expectedEventHistoryRequest) {
    assertDomainEventsCreatedForApplications(
        applications,
        caseWorkerId,
        expectedDomainEventType,
        expectedEventHistoryRequest,
        ServiceName.CIVIL_APPLY);
  }

  public void assertDomainEventsCreatedForApplications(
      List<ApplicationEntity> applications,
      UUID caseWorkerId,
      DomainEventType expectedDomainEventType,
      EventHistoryRequest expectedEventHistoryRequest,
      ServiceName expectedServiceName) {

    List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

    assertEquals(applications.size(), domainEvents.size());

    List<UUID> applicationIds =
        applications.stream().map(ApplicationEntity::getId).collect(Collectors.toList());

    for (DomainEventEntity domainEvent : domainEvents) {
      assertEquals(expectedDomainEventType, domainEvent.getType());
      assertTrue(applicationIds.contains(domainEvent.getApplicationId()));
      assertEquals(caseWorkerId, domainEvent.getCaseworkerId());
      assertThat(domainEvent.getServiceName()).isEqualTo(expectedServiceName);
      if (expectedEventHistoryRequest.getEventDescription() != null) {
        assertTrue(
            domainEvent.getData().contains(expectedEventHistoryRequest.getEventDescription()));
      } else {
        assertFalse(domainEvent.getData().contains("eventDescription"));
      }
    }
  }

  public void assertDomainEventForApplication(
      ApplicationEntity application, DomainEventType expectedType) throws Exception {
    assertDomainEventForApplication(application, expectedType, ServiceName.CIVIL_APPLY);
  }

  public void assertDomainEventForApplication(
      ApplicationEntity application, DomainEventType expectedType, ServiceName expectedServiceName)
      throws Exception {

    List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

    DomainEventEntity event = domainEvents.getFirst();

    assertThat(event.getApplicationId()).isEqualTo(application.getId());
    assertThat(event.getType()).isEqualTo(expectedType);
    assertThat(event.getCreatedAt()).isNotNull();
    assertThat(event.getServiceName()).isEqualTo(expectedServiceName);

    // ---- JSON payload assertions ----
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(event.getData());

    assertThat(json.get("applicationId").asString()).isEqualTo(application.getId().toString());

    assertThat(json.get("applicationStatus").asString()).isEqualTo(application.getStatus().name());

    assertThat(json.get("request").asString()).contains("{"); // stored as stringified JSON

    assertThat(json.has("createdDate")).isTrue();
    assertThat(json.get("createdDate").asString()).isEqualTo(application.getCreatedAt().toString());
  }
}
