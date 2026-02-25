package uk.gov.justice.laa.dstew.access.service.domainEvent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class GetEventsTest extends BaseServiceTest {

    @Autowired
    private DomainEventService serviceUnderTest;

    @Test
    void givenExpectedDomainEvents_whenGetEvents_thenReturnDomainEventsInCreatedAtOrder() {
        // given
        setSecurityContext(TestConstants.Roles.ADMIN);
        List<DomainEventEntity> generatedDomainEvents = DataGenerator.createMultipleDefault(
                DomainEventGenerator.class,20);

        List<DomainEventEntity> expectedDomainEvents = generatedDomainEvents.stream()
                .sorted((de1, de2) -> de2.getCreatedAt().compareTo(de1.getCreatedAt()))
                .toList();
        List<DomainEventEntity> orderedExpectedDomainEvents = generatedDomainEvents.stream()
                .sorted((de1, de2) -> de1.getCreatedAt().compareTo(de2.getCreatedAt()))
                .toList();

        when(domainEventRepository.findAll(any(Specification.class))).thenReturn(expectedDomainEvents);

        // when
        List<ApplicationDomainEvent> actualDomainEvents = serviceUnderTest.getEvents(
                UUID.randomUUID(),
                List.of(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
        );

        // then
        verify(domainEventRepository).findAll(any(Specification.class));
        assertDomainEventsEqual(orderedExpectedDomainEvents, actualDomainEvents);
    }

    @Test
    public void givenNotRoleReader_whenGetEvents_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.NO_ROLE);

        // when
        // then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getEvents(
                        null,
                        null
                ))
                .withMessageContaining("Access Denied");
        verify(domainEventRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenNoRole_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
        // given
        // when
        // then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getEvents(
                        null,
                        null
                ))
                .withMessageContaining("Access Denied");
        verify(applicationSummaryRepository, never()).findAll();
    }

    private void assertDomainEventsEqual(List<DomainEventEntity> expected, List<ApplicationDomainEvent> actual) {
        assertThat(expected.size()).isEqualTo(actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertDomainEventEqual(expected.get(i), actual.get(i));
        }
    }

    private void assertDomainEventEqual(DomainEventEntity expected, ApplicationDomainEvent actual) {
        assertThat(expected.getApplicationId()).isEqualTo(actual.getApplicationId());
        assertThat(expected.getCaseworkerId()).isEqualTo(actual.getCaseworkerId());
        assertThat(expected.getType().name()).isEqualTo(actual.getDomainEventType().name());
        assertThat(expected.getData()).isEqualTo(actual.getEventDescription());
        assertThat(expected.getCreatedAt()).isEqualTo(actual.getCreatedAt().toInstant());
        assertThat(expected.getCreatedBy()).isEqualTo(actual.getCreatedBy());
    }
}
