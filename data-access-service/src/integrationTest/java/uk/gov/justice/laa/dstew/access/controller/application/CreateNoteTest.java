package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.CreateNoteRequestGenerator;

@ActiveProfiles("test")
public class CreateNoteTest extends BaseIntegrationTest {

  @ParameterizedTest
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidCreateNoteRequestAndInvalidHeader_whenCreateNote_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  void givenValidCreateNoteRequestAndNoHeader_whenCreateNote_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    MvcResult result =
        postUri(
            TestConstants.URIs.CREATE_NOTES,
            DataGenerator.createDefault(CreateNoteRequestGenerator.class),
            ServiceNameHeader(serviceName),
            UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenApplicationExist_whenCreateNote_thenReturnOK() throws Exception {

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.caseworker(CaseworkerJohnDoe));
    CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class);

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, application.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);
    List<NoteEntity> createdNotes = noteRepository.findByApplicationId(application.getId());
    assertEquals(1, createdNotes.size());
    assertEquals(request.getNotes(), createdNotes.getFirst().getNotes());
    assertCreateNoteDomainEvent(application);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenNoApplicationExist_whenCreateNote_thenReturnNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class);

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, applicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertThat(problemDetail.getDetail())
        .contains("No application found with id: " + applicationId);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 10001})
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenApplicationExistAndNotesOutsideOfRange_whenCreateNote_thenReturnBadRequest(
      int total) throws Exception {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("Q".repeat(total)));

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, applicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertBadRequest(result);
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertThat(problemDetail.getDetail()).contains("Request validation failed");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10000})
  @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
  public void givenApplicationExistAndNotesAtEndsOfRange_whenCreateNote_thenReturnOk(int total)
      throws Exception {
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.caseworker(CaseworkerJohnDoe));

    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("Q".repeat(total)));

    // when
    MvcResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, application.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);
    List<NoteEntity> createdNotes = noteRepository.findByApplicationId(application.getId());
    assertEquals(1, createdNotes.size());
    assertEquals(request.getNotes(), createdNotes.getFirst().getNotes());
    assertCreateNoteDomainEvent(application);
  }

  private void assertCreateNoteDomainEvent(ApplicationEntity application) throws Exception {
    List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

    DomainEventEntity event = domainEvents.getFirst();

    assertThat(event.getApplicationId()).isEqualTo(application.getId());
    assertThat(event.getType()).isEqualTo(DomainEventType.APPLICATION_NOTES);
    assertThat(event.getCaseworkerId()).isEqualTo(application.getCaseworker().getId());
    assertThat(event.getCreatedAt()).isNotNull();
    ObjectMapper mapper = new ObjectMapper();
    JsonNode json = mapper.readTree(event.getData());

    assertThat(json.get("applicationId").asString()).isEqualTo(application.getId().toString());

    assertThat(json.get("caseworkerId").asString())
        .isEqualTo(application.getCaseworker().getId().toString());

    assertThat(json.get("request").asString()).contains("\"notes\"");

    assertThat(json.has("createdDate")).isTrue();
  }
}
