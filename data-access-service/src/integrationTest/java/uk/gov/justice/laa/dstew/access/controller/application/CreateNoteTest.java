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
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.CreateNoteRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class CreateNoteTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidCreateNoteRequestAndInvalidHeader_whenCreateNote_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenValidCreateNoteRequestAndNoHeader_whenCreateNote_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    HarnessResult result =
        postUri(
            TestConstants.URIs.CREATE_NOTES,
            DataGenerator.createDefault(CreateNoteRequestGenerator.class),
            ServiceNameHeader(serviceName),
            UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @SmokeTest
  @Test
  public void givenApplicationExist_whenCreateNote_thenReturnOK() throws Exception {

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class);

    // when
    HarnessResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, application.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);
    List<NoteEntity> createdNotes = noteRepository.findByApplicationId(application.getId());
    assertEquals(1, createdNotes.size());
    assertEquals(request.getNotes(), createdNotes.getFirst().getNotes());
  }

  @Test
  public void givenNoApplicationExist_whenCreateNote_thenReturnNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class);

    // when
    HarnessResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, applicationId);

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
  public void givenApplicationExistAndNotesOutsideOfRange_whenCreateNote_thenReturnBadRequest(
      int total) throws Exception {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("Q".repeat(total)));

    // when
    HarnessResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, applicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertBadRequest(result);
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertThat(problemDetail.getDetail()).contains("Request validation failed");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10000})
  public void givenApplicationExistAndNotesAtEndsOfRange_whenCreateNote_thenReturnOk(int total)
      throws Exception {
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("Q".repeat(total)));

    // when
    HarnessResult result = postUri(TestConstants.URIs.CREATE_NOTES, request, application.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);
    List<NoteEntity> createdNotes = noteRepository.findByApplicationId(application.getId());
    assertEquals(1, createdNotes.size());
    assertEquals(request.getNotes(), createdNotes.getFirst().getNotes());
  }
}
