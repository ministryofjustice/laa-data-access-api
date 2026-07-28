package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

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
import uk.gov.justice.laa.dstew.access.query.draft.DraftRepository;

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

  private static final String APPLICATIONS = "/api/v0/applications";

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private DraftRepository draftRepository;

  @Test
  void givenNewId_whenPutDraftApplication_thenReturns204AndStoresBody() {
    UUID id = UUID.randomUUID();

    ResponseEntity<Void> response =
        putDraft(id, Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(draftRepository.findById(id)).isPresent();
    assertThat(draftRepository.findById(id).get().getContent())
        .containsEntry("laaReference", "LAA-DRAFT-1");
  }

  @Test
  void givenExistingDraft_whenUpdated_thenReturns204AndOverwritesBody() {
    UUID id = UUID.randomUUID();
    putDraft(id, Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-1"));

    ResponseEntity<Void> response =
        putDraft(id, Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-2"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(draftRepository.findById(id).get().getContent())
        .containsEntry("laaReference", "LAA-DRAFT-2");
  }

  @Test
  void givenSubmittedApplication_whenPutDraftAgain_thenReturns409() {
    UUID id = UUID.randomUUID();
    restTemplate.exchange(
        APPLICATIONS + "/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(validCreateApplicationRequest(id, UUID.randomUUID()), headers()),
        Void.class);
    restTemplate.exchange(
        APPLICATIONS + "/" + id + "/submit",
        HttpMethod.POST,
        new HttpEntity<>(null, headers()),
        Void.class);

    ResponseEntity<String> response =
        restTemplate.exchange(
            APPLICATIONS + "/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("status", "DRAFT"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private ResponseEntity<Void> putDraft(UUID id, Map<String, Object> body) {
    return restTemplate.exchange(
        APPLICATIONS + "/" + id, HttpMethod.PUT, new HttpEntity<>(body, headers()), Void.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
