package uk.gov.justice.laa.dstew.access.service.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.Application.verifyThatApplicationEntitySaved;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.DomainEvent.verifyThatDomainEventSaved;

public class ReassignCaseworkerTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Test
    void givenApplicationWithCaseworker_whenReassignCaseworker_thenSaveAndCreateDomainEvent() throws JsonProcessingException {

        // given
        UUID applicationId = UUID.randomUUID();

        CaseworkerEntity existingCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder ->
                builder.id(UUID.randomUUID())
                        .username("John Doe")
        );

        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder ->
                builder.id(UUID.randomUUID())
                        .username("Jane Doe")
        );

        ApplicationEntity existingApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
                builder.id(applicationId).caseworker(existingCaseworker)
        );

        ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(expectedCaseworker).build();

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription("Case reassigned.")
                .build();

        DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(expectedCaseworker.getId())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(objectMapper.writeValueAsString(AssignApplicationDomainEventDetails.builder()
                        .applicationId(existingApplicationEntity.getId())
                        .caseWorkerId(expectedCaseworker.getId())
                        .eventDescription(eventHistory.getEventDescription())
                        .createdBy("")
                        .build()))
                .build();

        List<UUID> applicationIds = List.of(applicationId);

        when(applicationRepository.findAllById(eq(applicationIds))).thenReturn(List.of(existingApplicationEntity));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), List.of(applicationId), eventHistory);

        // then
        verify(applicationRepository, times(1)).findAllById(eq(applicationIds));
        verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());

        verifyThatApplicationEntitySaved(applicationRepository, expectedApplicationEntity, 1);
        verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    }
}
