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
import uk.gov.justice.laa.dstew.access.query.workitem.WorkloadPage;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-workitem-assignment;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkItemAssignmentInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void givenApplicationWorkItem_whenAssigned_thenReturns204AndReflectedInWorkload() {
    UUID applicationId = submitApplication();
    UUID caseworkerId = UUID.randomUUID();

    ResponseEntity<Void> response = assign(applicationId, caseworkerId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(assigneeOf(applicationId)).isEqualTo(caseworkerId);
  }

  @Test
  void givenPriorAuthorityWorkItem_whenAssigned_thenReturns204AndReflectedInWorkload() {
    UUID applicationId = submitApplication();
    UUID priorAuthorityId = submitPriorAuthority(applicationId);
    UUID caseworkerId = UUID.randomUUID();

    ResponseEntity<Void> response = assign(priorAuthorityId, caseworkerId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(assigneeOf(priorAuthorityId)).isEqualTo(caseworkerId);
  }

  @Test
  void givenAssignedItem_whenAssignedSameCaseworker_thenIdempotent204() {
    UUID applicationId = submitApplication();
    UUID caseworkerId = UUID.randomUUID();
    assign(applicationId, caseworkerId);

    ResponseEntity<Void> response = assign(applicationId, caseworkerId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenAssignedItem_whenAssignedDifferentCaseworker_thenReturns409() {
    UUID applicationId = submitApplication();
    assign(applicationId, UUID.randomUUID());

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/work-items/" + applicationId + "/assign",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("caseworkerId", UUID.randomUUID().toString()), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenAssignedItem_whenUnassigned_thenReturns204AndClearsAssignee() {
    UUID applicationId = submitApplication();
    UUID caseworkerId = UUID.randomUUID();
    assign(applicationId, caseworkerId);

    ResponseEntity<Void> response = unassign(applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(assigneeOf(applicationId)).isNull();
  }

  @Test
  void givenUnassignedItem_whenUnassigned_thenReturns409() {
    UUID applicationId = submitApplication();

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/work-items/" + applicationId + "/assignment",
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenUnknownWorkItem_whenAssigned_thenReturns404() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/work-items/" + UUID.randomUUID() + "/assign",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("caseworkerId", UUID.randomUUID().toString()), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenTwoPriorAuthoritiesOnOneApplication_whenAssignedSeparately_thenIndependent() {
    UUID applicationId = submitApplication();
    UUID priorAuthorityA = submitPriorAuthority(applicationId);
    UUID priorAuthorityB = submitPriorAuthority(applicationId);
    UUID caseworkerA = UUID.randomUUID();
    UUID caseworkerB = UUID.randomUUID();

    assertThat(assign(priorAuthorityA, caseworkerA).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(assign(priorAuthorityB, caseworkerB).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(assigneeOf(priorAuthorityA)).isEqualTo(caseworkerA);
    assertThat(assigneeOf(priorAuthorityB)).isEqualTo(caseworkerB);
    assertThat(assigneeOf(applicationId)).isNull();
  }

  private ResponseEntity<Void> assign(UUID workItemId, UUID caseworkerId) {
    return restTemplate.exchange(
        "/api/v0/work-items/" + workItemId + "/assign",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("caseworkerId", caseworkerId.toString()), headers()),
        Void.class);
  }

  private ResponseEntity<Void> unassign(UUID workItemId) {
    return restTemplate.exchange(
        "/api/v0/work-items/" + workItemId + "/assignment",
        HttpMethod.DELETE,
        new HttpEntity<>(headers()),
        Void.class);
  }

  private UUID assigneeOf(UUID workItemId) {
    WorkloadPage page =
        restTemplate
            .exchange(
                "/api/v0/workload?page=0&size=200",
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                WorkloadPage.class)
            .getBody();
    assertThat(page).isNotNull();
    return page.content().stream()
        .filter(item -> item.workItemId().equals(workItemId))
        .findFirst()
        .orElseThrow()
        .assignedCaseworkerId();
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
