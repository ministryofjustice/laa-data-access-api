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
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-prior-authority;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriorAuthorityCommandControllerInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void givenApplication_whenCreatePriorAuthority_thenReturns201WithLocation() {
    UUID applicationId = createApplication();

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            priorAuthorities(applicationId),
            new HttpEntity<>(Map.of("reference", "PA-1", "amount", 500), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .startsWith(priorAuthorities(applicationId) + "/");
  }

  @Test
  void givenExistingPriorAuthority_whenUpdated_thenReturns204() {
    UUID applicationId = createApplication();
    URI created =
        restTemplate
            .postForEntity(
                priorAuthorities(applicationId),
                new HttpEntity<>(Map.of("reference", "PA-1", "amount", 500), headers()),
                Void.class)
            .getHeaders()
            .getLocation();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            created,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("reference", "PA-1", "amount", 750), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenUnknownApplication_whenCreatePriorAuthority_thenReturns404() {
    UUID unknownApplicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorities(unknownApplicationId),
            new HttpEntity<>(Map.of("reference", "PA-1"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("No application found with ID: " + unknownApplicationId);
  }

  @Test
  void givenUnknownPriorAuthority_whenUpdated_thenReturns404() {
    UUID applicationId = createApplication();
    UUID unknownPriorAuthorityId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            priorAuthorities(applicationId) + "/" + unknownPriorAuthorityId,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("reference", "PA-1"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("No prior authority found with ID: " + unknownPriorAuthorityId);
  }

  @Test
  void givenPriorAuthorityDraft_whenSubmitted_thenReturns204() {
    UUID applicationId = createApplication();
    URI created =
        restTemplate
            .postForEntity(
                priorAuthorities(applicationId),
                new HttpEntity<>(Map.of("reference", "PA-1", "amount", 500), headers()),
                Void.class)
            .getHeaders()
            .getLocation();

    ResponseEntity<Void> response =
        restTemplate.postForEntity(created + "/submit", new HttpEntity<>(headers()), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenDraftApplication_whenCreatePriorAuthority_thenReturns409() {
    UUID draftApplicationId = createDraftApplication();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorities(draftApplicationId),
            new HttpEntity<>(Map.of("reference", "PA-1"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private UUID createDraftApplication() {
    UUID applicationId = UUID.randomUUID();
    restTemplate.exchange(
        "/api/v0/applications/" + applicationId,
        HttpMethod.PUT,
        new HttpEntity<>(Map.of("status", "DRAFT"), headers()),
        Void.class);
    return applicationId;
  }

  private UUID createApplication() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    restTemplate.exchange(
        "/api/v0/applications/" + applicationId,
        HttpMethod.PUT,
        new HttpEntity<>(request, headers()),
        Void.class);
    restTemplate.postForEntity(
        "/api/v0/applications/" + applicationId + "/submit",
        new HttpEntity<>(headers()),
        Void.class);
    return applicationId;
  }

  private static String priorAuthorities(UUID applicationId) {
    return "/api/v0/applications/" + applicationId + "/prior-authorities";
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
