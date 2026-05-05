package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;
import uk.gov.justice.laa.dstew.access.utils.helpers.DateTimeHelper;

public class GetDomainEventTest extends BaseHarnessTest {

  private final String SEARCH_EVENT_TYPE_PARAM = "eventType=";

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void
      givenApplicationWithDomainEventsAndNoHeader_whenApplicationHistorySearch_thenReturnBadRequest(
          String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void
      givenApplicationWithDomainEventsAndInvalidHeader_whenApplicationHistorySearch_thenReturnBadRequest()
          throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    HarnessResult result =
        getUri(
            TestConstants.URIs.APPLICATION_HISTORY_SEARCH,
            ServiceNameHeader(serviceName),
            UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  public void givenApplicationWithDomainEvents_whenApplicationHistorySearch_theReturnDomainEvents()
      throws Exception {
    var appId = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class).getId();
    // given
    var domainEvents = setUpDomainEvents(appId);
    var expectedDomainEvents = domainEvents.stream().map(GetDomainEventTest::toEvent).toList();

    // when
    HarnessResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, appId);
    ApplicationHistoryResponse actualResponse =
        deserialise(result, ApplicationHistoryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    assertThat(actualResponse).isNotNull();
    assertTrue(actualResponse.getEvents().containsAll(expectedDomainEvents));
  }

  @Test
  public void
      givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterSingleDomainEvent_thenOnlyFilteredDomainEventTypes()
          throws Exception {
    var appId = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class).getId();
    // given
    var domainEvents = setUpDomainEvents(appId);
    var expectedAssignDomainEvents =
        domainEvents.stream()
            .filter(s -> s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER))
            .map(GetDomainEventTest::toEvent)
            .toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.APPLICATION_HISTORY_SEARCH
                + "?"
                + SEARCH_EVENT_TYPE_PARAM
                + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
            appId);
    ApplicationHistoryResponse actualResponse =
        deserialise(result, ApplicationHistoryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    assertThat(actualResponse).isNotNull();
    assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
  }

  @Test
  public void
      givenApplicationWithDomainEvents_whenApplicationHistorySearchFilterMultipleDomainEvent_thenOnlyFilteredDomainEventTypes()
          throws Exception {
    var appId = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class).getId();
    // given
    var domainEvents = setUpDomainEvents(appId);
    var expectedAssignDomainEvents =
        domainEvents.stream()
            .filter(
                s ->
                    s.getType().equals(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                        || s.getType().equals(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER))
            .map(GetDomainEventTest::toEvent)
            .toList();

    // when
    String address =
        TestConstants.URIs.APPLICATION_HISTORY_SEARCH
            + "?"
            + SEARCH_EVENT_TYPE_PARAM
            + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER
            + "&"
            + SEARCH_EVENT_TYPE_PARAM
            + DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER;

    HarnessResult result = getUri(address, appId);
    ApplicationHistoryResponse actualResponse =
        deserialise(result, ApplicationHistoryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    assertThat(actualResponse).isNotNull();
    assertTrue(actualResponse.getEvents().containsAll(expectedAssignDomainEvents));
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenApplicationHistorySearch_thenReturnUnauthorised() throws Exception {
    withNoToken();
    HarnessResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  public void givenNoRole_whenApplicationHistorySearch_thenReturnForbidden() throws Exception {
    withUnknownToken();
    HarnessResult result = getUri(TestConstants.URIs.APPLICATION_HISTORY_SEARCH, UUID.randomUUID());

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  private List<DomainEventEntity> setUpDomainEvents(UUID appId) {
    return List.of(
        setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
        setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
        setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER),
        setupDomainEvent(appId, DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER),
        setupDomainEvent(appId, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));
  }

  private DomainEventEntity setupDomainEvent(UUID appId, DomainEventType eventType) {
    String eventDesc = "{\"eventDescription\": \"" + eventType.getValue() + "\"}";
    return persistedDataGenerator.createAndPersist(
        DomainEventGenerator.class,
        builder ->
            builder
                .applicationId(appId)
                .caseworkerId(CaseworkerJohnDoe.getId())
                .createdAt(DateTimeHelper.GetSystemInstanceWithoutNanoseconds())
                .data(eventDesc)
                .type(eventType));
  }

  private static ApplicationDomainEventResponse toEvent(DomainEventEntity entity) {
    return ApplicationDomainEventResponse.builder()
        .applicationId(entity.getApplicationId())
        .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
        .domainEventType(entity.getType())
        .eventDescription(entity.getData())
        .caseworkerId(entity.getCaseworkerId())
        .createdBy(entity.getCreatedBy())
        .build();
  }
}
