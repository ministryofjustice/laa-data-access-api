package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.stream;
import static uk.gov.justice.laa.dstew.access.Constants.POSTGRES_INSTANCE;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

@Testcontainers
@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class DomainEventSpecificationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_INSTANCE);

    @Autowired
    private DomainEventRepository repository;
    @Autowired
    private ApplicationRepository applicationRepository;

    private List<DomainEventEntity> prePopulatedEvents;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() throws Exception {
        var applications = List.of(createApplication(), createApplication());
        applicationRepository.saveAllAndFlush(applications);
        prePopulatedEvents = applications.stream().map(app -> createEntities(app.getId())).flatMap(Collection::stream).toList();

        repository.saveAllAndFlush(prePopulatedEvents);
    }

    @Test
    void givenApplicationIdSpecification_shouldFilterApplicationsToGivenId() {
        var filterIdSpecification = DomainEventSpecification.filterApplicationId(prePopulatedEvents.getFirst().getApplicationId());
        var result = repository.findAll(filterIdSpecification);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getId()).isEqualTo(prePopulatedEvents.getFirst().getId());
        assertThat(result)
            .allMatch(event -> event.getApplicationId().equals(prePopulatedEvents.getFirst().getApplicationId()));
    }

    @Test
    void givenDomainEventTypeSpecification_withSingleEventType_shouldFilterToOnlyThatEventType() {
        var appId = prePopulatedEvents.getFirst().getApplicationId();
        var filter = DomainEventSpecification.filterApplicationId(appId)
                                             .and(DomainEventSpecification.filterMultipleEventType(List.of(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)));
        var result = repository.findAll(filter);
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(event -> event.getApplicationId().equals(appId));
        assertThat(result).allMatch(event -> event.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));
    }

    @Test
    void givenDomainEventTypeSpecification_withMultipleEventType_shouldFilterToAllMatchingEventTypes() {
        var appId = prePopulatedEvents.getFirst().getApplicationId();
        var filter = DomainEventSpecification.filterApplicationId(appId)
                                             .and(DomainEventSpecification.filterMultipleEventType(List.of(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER, 
                                                                                                    DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER)));
        var result = repository.findAll(filter);
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(event -> event.getApplicationId().equals(appId));
    }

    private List<DomainEventEntity> createEntities(UUID appId) {
        return List.of(createEntity(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER), 
                       createEntity(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER), 
                       createEntity(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER));
    }

    private DomainEventEntity createEntity(UUID appId, DomainEventType eventType) {
        return DomainEventEntity.builder()
                                .applicationId(appId)
                                .createdAt(Instant.now())
                                .createdBy("John.Doe")
                                .data("{ \"eventDescription\" : \"assigning a caseworker\" }")
                                .type(eventType)
                                .build();
    }

    private ApplicationEntity createApplication() {
        Set individuals = Set.of(IndividualEntity.builder()
                                                 .firstName("John")
                                                 .lastName("Doe")
                                                 .dateOfBirth(LocalDate.of(2000, 01, 01))
                                                 .individualContent(Map.of("foo", "bar"))
                                                 .build());
        return ApplicationEntity.builder()
                                .applicationContent(Map.of("foo","bar"))
                                .laaReference("ref")
                                .createdAt(Instant.now())
                                .individuals(individuals)
                                .status(ApplicationStatus.IN_PROGRESS)
                                .build();
    }
}
