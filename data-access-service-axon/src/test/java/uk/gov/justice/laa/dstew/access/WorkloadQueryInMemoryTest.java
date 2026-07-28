package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkType;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkloadPage;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-workload;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkloadQueryInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void givenSubmittedApplicationAndPriorAuthority_whenGetWorkload_thenReturnsBothItems() {
    UUID applicationId = submitApplication();
    UUID priorAuthorityId = submitPriorAuthority(applicationId);

    ResponseEntity<WorkloadPage> response =
        restTemplate.exchange(
            "/api/v0/workload", HttpMethod.GET, new HttpEntity<>(headers()), WorkloadPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    WorkloadPage page = response.getBody();
    assertThat(page).isNotNull();
    assertThat(page.content())
        .extracting(item -> item.workItemId())
        .contains(applicationId, priorAuthorityId);
    assertThat(page.content())
        .filteredOn(item -> item.workItemId().equals(applicationId))
        .singleElement()
        .satisfies(item -> assertThat(item.laaReference()).isEqualTo("LAA-123"));
  }

  @Test
  void givenWorkTypeFilter_whenGetWorkload_thenReturnsOnlyMatchingType() {
    UUID applicationId = submitApplication();
    UUID priorAuthorityId = submitPriorAuthority(applicationId);

    ResponseEntity<WorkloadPage> response =
        restTemplate.exchange(
            "/api/v0/workload?work_type=PRIOR_AUTHORITY",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            WorkloadPage.class);

    WorkloadPage page = response.getBody();
    assertThat(page).isNotNull();
    assertThat(page.content())
        .isNotEmpty()
        .allSatisfy(item -> assertThat(item.workType()).isEqualTo(WorkType.PRIOR_AUTHORITY));
    assertThat(page.content())
        .filteredOn(item -> item.workItemId().equals(priorAuthorityId))
        .singleElement()
        .satisfies(item -> assertThat(item.applicationId()).isEqualTo(applicationId));
  }

  @Test
  void givenUnassignedFilter_whenGetWorkload_thenReturnsUnassignedPool() {
    submitApplication();

    ResponseEntity<WorkloadPage> response =
        restTemplate.exchange(
            "/api/v0/workload?unassigned=true",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            WorkloadPage.class);

    WorkloadPage page = response.getBody();
    assertThat(page).isNotNull();
    assertThat(page.content()).isNotEmpty();
    assertThat(page.content()).allSatisfy(item -> assertThat(item.assignedCaseworkerId()).isNull());
  }

  @Test
  void givenBadWorkTypeFilter_whenGetWorkload_thenReturnsBadRequest() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/workload?work_type=NONSENSE",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private UUID submitApplication() {
    UUID applicationId = UUID.randomUUID();
    restTemplate.exchange(
        "/api/v0/applications/" + applicationId,
        HttpMethod.PUT,
        new HttpEntity<>(
            validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
        Void.class);
    restTemplate.postForEntity(
        "/api/v0/applications/" + applicationId + "/submit",
        new HttpEntity<>(headers()),
        Void.class);
    return applicationId;
  }

  private UUID submitPriorAuthority(UUID applicationId) {
    URI created =
        restTemplate
            .postForEntity(
                "/api/v0/applications/" + applicationId + "/prior-authorities",
                new HttpEntity<>(Map.of("reference", "PA-1", "amount", 500), headers()),
                Void.class)
            .getHeaders()
            .getLocation();
    restTemplate.postForEntity(created + "/submit", new HttpEntity<>(headers()), Void.class);
    String path = created.getPath();
    return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
