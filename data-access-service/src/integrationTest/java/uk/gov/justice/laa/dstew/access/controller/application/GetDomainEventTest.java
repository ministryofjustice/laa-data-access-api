package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.DateTimeHelper;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

public class GetDomainEventTest extends BaseIntegrationTest {

    private final String SEARCH_EVENT_TYPE_PARAM = "eventType=";

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationWithDomainEvents_whenApplicationHistorySearch_theReturnDomainEvents() throws Exception {
        var appId = persistedApplicationFactory.createAndPersist().getId();
        // given
        var domainEvents = setUpDomainEvents(appId);
        var expectedDomainEvents = domainEvents.stream().map(GetDomainEventTest::toEvent).toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, appId);
        ApplicationHistoryResponse actualResponse = deserialise(result, ApplicationHistoryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        assertThat(actualResponse).isNotNull();
        assertTrue(actualResponse.getEvents().containsAll(expectedDomainEvents));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterSingleDomainEvent_thenOnlyFilteredDomainEventTypes() throws Exception {
        var appId = persistedApplicationFactory.createAndPersist().getId();
        // given
        var domainEvents = setUpDomainEvents(appId);
        var expectedAssignDomainEvents = domainEvents.stream()
                .filter(s -> s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER))
                .map(GetDomainEventTest::toEvent)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH +
                        "?" + SEARCH_EVENT_TYPE_PARAM + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
                appId);
        ApplicationHistoryResponse actualResponse = deserialise(result, ApplicationHistoryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        assertThat(actualResponse).isNotNull();
        assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterMultipleDomainEvent_thenOnlyFilteredDomainEventTypes() throws Exception {
        var appId = persistedApplicationFactory.createAndPersist().getId();
        // given
        var domainEvents = setUpDomainEvents(appId);
        var expectedAssignDomainEvents = domainEvents.stream()
                .filter(s -> s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER) ||
                        s.getType().equals(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER))
                .map(GetDomainEventTest::toEvent)
                .toList();

        // when
        String address = TestConstants.URIs.APPLICATION_HISTORY_SEARCH +
                "?" + SEARCH_EVENT_TYPE_PARAM +
                DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER +
                "&" + SEARCH_EVENT_TYPE_PARAM +
                DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER;

        MvcResult result = getUri(address, appId);
        ApplicationHistoryResponse actualResponse = deserialise(result, ApplicationHistoryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        assertThat(actualResponse).isNotNull();
        assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
    }

    @Test
    public void givenNoUser_whenApplicationHistorySearch_thenReturnUnauthorised() throws Exception {
        // when
        MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenNoRole_whenApplicationHistorySearch_thenReturnForbidden() throws Exception {
        // when
        MvcResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    private List<DomainEventEntity> setUpDomainEvents(UUID appId) {
        return List.of(
                setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
                setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
                setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
                setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
                setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
        );
    }

    private DomainEventEntity setupDomainEvent(UUID appId, DomainEventType eventType) {
        String eventDesc = "{\"eventDescription\": \"" + eventType.getValue() + "\"}";
        return persistedDomainEventFactory.createAndPersist(builder ->
                {
                    builder.applicationId(appId);
                    builder.createdAt(DateTimeHelper.GetSystemInstanceWithoutNanoseconds());
                    builder.data(eventDesc);
                    builder.type(eventType);
                }
        );
    }

    private static ApplicationDomainEvent toEvent(DomainEventEntity entity) {
        return ApplicationDomainEvent.builder()
                .applicationId(entity.getApplicationId())
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .domainEventType(entity.getType())
                .eventDescription(entity.getData())
                .caseworkerId(entity.getCaseworkerId())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
