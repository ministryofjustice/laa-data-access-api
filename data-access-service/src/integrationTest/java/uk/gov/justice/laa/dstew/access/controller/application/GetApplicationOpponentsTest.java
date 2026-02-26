package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

public class GetApplicationOpponentsTest extends BaseIntegrationTest {

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenApplicationWithOpponents_whenGetApplication_thenReturnsOpponents() throws Exception {

    Map<String, Object> opposable = Map.of(
        "opposableType", "ApplicationMeritsTask::Individual",
        "firstName", "John",
        "lastName", "Smith",
        "name", "Acme Ltd"
    );

    Map<String, Object> opponent = Map.of(
        "opposable", opposable
    );

    Map<String, Object> merits = Map.of(
        "opponents", List.of(opponent)
    );

    Map<String, Object> content = Map.of(
        "applicationMerits", merits
    );

    ApplicationEntity application = persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder -> builder
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(content)
            .createdAt(Instant.now().minusSeconds(10000))
            .modifiedAt(Instant.now())
    );

    MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application response = deserialise(result, Application.class);

    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    assertThat(response.getOpponents()).isNotNull();
    assertThat(response.getOpponents()).hasSize(1);

    var mapped = response.getOpponents().get(0);
    assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    assertThat(mapped.getFirstName()).isEqualTo("John");
    assertThat(mapped.getLastName()).isEqualTo("Smith");
    assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }


  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenApplicationWithEmptyOpponents_whenGetApplication_thenReturnsEmptyList() throws Exception {

    Map<String, Object> merits = Map.of(
        "opponents", List.of()
    );

    Map<String, Object> content = Map.of(
        "applicationMerits", merits
    );

    ApplicationEntity application = persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder -> builder.applicationContent(content)
    );

    MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application response = deserialise(result, Application.class);

    assertOK(result);
    assertThat(response.getOpponents()).isNotNull();
    assertThat(response.getOpponents()).isEmpty();
  }


  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenApplicationWithoutOpponentsSection_whenGetApplication_thenOpponentsIsEmpty() throws Exception {

    Map<String, Object> content = Map.of(
        "someOtherKey", "value"
    );

    ApplicationEntity application = persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder -> builder.applicationContent(content)
    );

    MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application response = deserialise(result, Application.class);

    assertOK(result);
    assertThat(response.getOpponents()).isEmpty();
  }


  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenOpponentWithMissingFirstName_whenGetApplication_thenReturnsRemainingFields() throws Exception {

    Map<String, Object> opposable = Map.of(
        "opposableType", "ApplicationMeritsTask::Individual",
        // firstName intentionally missing
        "lastName", "Smith",
        "name", "Acme Ltd"
    );

    Map<String, Object> opponent = Map.of(
        "opposable", opposable
    );

    Map<String, Object> merits = Map.of(
        "opponents", List.of(opponent)
    );

    Map<String, Object> content = Map.of(
        "applicationMerits", merits
    );

    ApplicationEntity application = persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder -> builder.applicationContent(content)
    );

    MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application response = deserialise(result, Application.class);

    assertOK(result);

    assertThat(response.getOpponents()).isNotNull();
    assertThat(response.getOpponents()).hasSize(1);

    var mapped = response.getOpponents().get(0);
    assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    assertThat(mapped.getFirstName()).isNull();
    assertThat(mapped.getLastName()).isEqualTo("Smith");
    assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }
}