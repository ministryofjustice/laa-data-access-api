package uk.gov.justice.laa.dstew.access.controller.application;

import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerAssignRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class ReassignCaseworkerTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidReassignRequestAndInvalidHeader_whenAssignCaseworker_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenValidReassignRequestAndNoHeader_whenAssignCaseworker_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {

    CaseworkerAssignRequest caseworkerReassignRequest =
        DataGenerator.createDefault(
            CaseworkerAssignRequestGenerator.class,
            builder -> {
              builder
                  .caseworkerId(CaseworkerJaneDoe.getId())
                  .applicationIds(List.of(UUID.randomUUID()))
                  .eventHistory(
                      EventHistoryRequest.builder()
                          .eventDescription("Assigning caseworker")
                          .build());
            });

    HarnessResult result =
        postUri(
            TestConstants.URIs.ASSIGN_CASEWORKER,
            caseworkerReassignRequest,
            ServiceNameHeader(serviceName));

    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @SmokeTest
  @Test
  public void givenValidReassignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker()
      throws Exception {
    // given
    List<ApplicationEntity> toReassignedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 4, builder -> builder.caseworker(CaseworkerJohnDoe));

    List<ApplicationEntity> expectedReassignedApplications =
        toReassignedApplications.stream()
            .peek(application -> application.setCaseworker(CaseworkerJaneDoe))
            .toList();

    List<ApplicationEntity> expectedAlreadyAssignedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 5, builder -> builder.caseworker(CaseworkerJaneDoe));

    List<ApplicationEntity> expectedUnassignedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 8, builder -> builder.caseworker(null));

    CaseworkerAssignRequest caseworkerReassignRequest =
        DataGenerator.createDefault(
            CaseworkerAssignRequestGenerator.class,
            builder -> {
              builder
                  .caseworkerId(CaseworkerJaneDoe.getId())
                  .applicationIds(
                      toReassignedApplications.stream()
                          .map(ApplicationEntity::getId)
                          .collect(Collectors.toList()))
                  .eventHistory(
                      EventHistoryRequest.builder()
                          .eventDescription("Assigning caseworker")
                          .build());
            });

    // when
    HarnessResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerReassignRequest);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    applicationAsserts.assertApplicationsMatchInRepositoryIgnoringLastUpdated(
        expectedReassignedApplications);
    applicationAsserts.assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
    applicationAsserts.assertApplicationsMatchInRepository(expectedUnassignedApplications);
    domainEventAsserts.assertDomainEventsCreatedForApplications(
        toReassignedApplications,
        CaseworkerJaneDoe.getId(),
        DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
        caseworkerReassignRequest.getEventHistory());
  }
}
