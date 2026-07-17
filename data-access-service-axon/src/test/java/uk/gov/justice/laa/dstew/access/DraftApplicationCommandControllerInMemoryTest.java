package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-draft-application;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DraftApplicationCommandControllerInMemoryTest {

  private static final String DRAFT_APPLICATIONS = "/api/v0/draft-applications";

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void givenValidBody_whenCreateDraftApplication_thenReturns201WithLocation() {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            DRAFT_APPLICATIONS,
            new HttpEntity<>(Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-1"), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath()).startsWith(DRAFT_APPLICATIONS + "/");
  }

  @Test
  void givenExistingDraftApplication_whenUpdated_thenReturns204() {
    URI created =
        restTemplate
            .postForEntity(
                DRAFT_APPLICATIONS,
                new HttpEntity<>(Map.of("status", "DRAFT"), headers()),
                Void.class)
            .getHeaders()
            .getLocation();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            created,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-2"), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenUnknownDraftApplication_whenUpdated_thenReturns404() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            DRAFT_APPLICATIONS + "/" + unknownId,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("status", "DRAFT"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("No draft application found with ID: " + unknownId);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
