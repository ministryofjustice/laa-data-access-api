package uk.gov.justice.laa.dstew.access.controller.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerUnassignRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class UnassignCaseworkerTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidUnassignRequestAndInvalidHeader_whenUnassignCaseworker_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenValidUnassignRequestAndNoHeader_whenUnassignCaseworker_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    ApplicationEntity toUnassignedApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.caseworker(CaseworkerJohnDoe);
            });

    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(
            CaseworkerUnassignRequestGenerator.class,
            builder -> {
              builder.eventHistory(
                  EventHistoryRequest.builder().eventDescription("Unassigned Caseworker").build());
            });
    ApplicationEntity expectedUnassignedApplication =
        toUnassignedApplication.toBuilder().caseworker(null).build();

    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            ServiceNameHeader(serviceName),
            expectedUnassignedApplication.getId());

    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  public void givenValidUnassignRequest_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
      throws Exception {
    // given
    ApplicationEntity toUnassignedApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.caseworker(CaseworkerJohnDoe);
            });

    ApplicationEntity expectedUnassignedApplication =
        toUnassignedApplication.toBuilder().caseworker(null).build();

    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(
            CaseworkerUnassignRequestGenerator.class,
            builder -> {
              builder.eventHistory(
                  EventHistoryRequest.builder().eventDescription("Unassigned Caseworker").build());
            });

    // when
    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            expectedUnassignedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    ApplicationEntity actual =
        applicationRepository.findById(expectedUnassignedApplication.getId()).orElseThrow();
    assertNull(actual.getCaseworker());
    assertEquals(
        applicationAsserts.createApplicationIgnoreLastUpdated(expectedUnassignedApplication),
        applicationAsserts.createApplicationIgnoreLastUpdated(actual));

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(expectedUnassignedApplication),
        null,
        DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
        caseworkerUnassignRequest.getEventHistory());
  }

  @Test
  public void
      givenValidUnassignRequestWithBlankEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
          throws Exception {
    // given
    ApplicationEntity toUnassignedApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.caseworker(CaseworkerJohnDoe);
            });

    ApplicationEntity expectedUnassignedApplication =
        toUnassignedApplication.toBuilder().caseworker(null).build();

    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(
            CaseworkerUnassignRequestGenerator.class,
            builder -> {
              builder.eventHistory(EventHistoryRequest.builder().eventDescription("").build());
            });

    // when
    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            expectedUnassignedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    ApplicationEntity actual =
        applicationRepository.findById(expectedUnassignedApplication.getId()).orElseThrow();
    assertNull(actual.getCaseworker());
    assertEquals(
        applicationAsserts.createApplicationIgnoreLastUpdated(expectedUnassignedApplication),
        applicationAsserts.createApplicationIgnoreLastUpdated(actual));

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(expectedUnassignedApplication),
        null,
        DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
        caseworkerUnassignRequest.getEventHistory());
  }

  @Test
  public void
      givenValidUnassignRequestWithNullEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker()
          throws Exception {
    // given
    ApplicationEntity expectedUnassignedApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.caseworker(CaseworkerJohnDoe);
            });

    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(
            CaseworkerUnassignRequestGenerator.class,
            builder -> {
              builder.eventHistory(EventHistoryRequest.builder().eventDescription(null).build());
            });

    // when
    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            expectedUnassignedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(expectedUnassignedApplication),
        null,
        DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
        caseworkerUnassignRequest.getEventHistory());
  }

  @Test
  public void givenApplicationNotExist_whenUnassignCaseworker_thenReturnNotFound()
      throws Exception {
    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID());

    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
  }

  @Test
  public void givenCaseworkerNotExist_whenUnassignCaseworker_thenReturnOK() throws Exception {
    // given
    ApplicationEntity expectedUnassignedApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.caseworker(null);
            });

    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(
            CaseworkerUnassignRequestGenerator.class,
            builder -> {
              builder.eventHistory(
                  EventHistoryRequest.builder().eventDescription("Unassigned Caseworker").build());
            });

    // when
    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            expectedUnassignedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    // TODO: is this right?
    assertOK(result);

    // Scoped to this application — avoids global count sensitivity
    assertEquals(
        0,
        domainEventRepository.findAll().stream()
            .filter(e -> e.getApplicationId().equals(expectedUnassignedApplication.getId()))
            .count());
  }

  @Test
  public void givenReaderRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
    withUnknownToken();
    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenUnknownRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
    withUnknownToken();
    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised() throws Exception {
    withNoToken();
    CaseworkerUnassignRequest caseworkerUnassignRequest =
        DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

    HarnessResult result =
        postUri(
            TestConstants.URIs.UNASSIGN_CASEWORKER,
            caseworkerUnassignRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }
}
