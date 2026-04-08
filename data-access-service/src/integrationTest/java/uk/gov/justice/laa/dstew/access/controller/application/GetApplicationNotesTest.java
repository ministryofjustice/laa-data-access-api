package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.NoteEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

public class GetApplicationNotesTest extends BaseHarnessTest {

    @Test
    void givenNoHeader_whenGetApplicationNotes_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenInvalidHeader_whenGetApplicationNotes_thenReturnBadRequest(String serviceName) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, ServiceNameHeader(serviceName), UUID.randomUUID());
        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

    @Test
    public void givenApplicationExistsWithNotes_whenGetApplicationNotes_thenReturnOk() throws Exception {
        // given
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        UUID applicationId = application.getId();

        persistedDataGenerator.createAndPersist(NoteEntityGenerator.class,
                builder -> builder.applicationId(applicationId).notes("first note").createdBy("user-a"));

        persistedDataGenerator.createAndPersist(NoteEntityGenerator.class,
                builder -> builder.applicationId(applicationId).notes("second note").createdBy("user-b"));

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, applicationId);

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        ApplicationNotesResponse response = deserialise(result, ApplicationNotesResponse.class);
        List<ApplicationNoteResponse> notes = response.getNotes();

        assertThat(notes).hasSize(2);
        assertThat(notes.get(0).getNote()).isEqualTo("first note");
        assertThat(notes.get(0).getCreatedBy()).isEqualTo("user-a");
        assertThat(notes.get(0).getCreatedAt()).isNotNull();
        assertThat(notes.get(1).getNote()).isEqualTo("second note");
        assertThat(notes.get(1).getCreatedBy()).isEqualTo("user-b");
        assertThat(notes.get(1).getCreatedAt()).isNotNull();
        // assert ascending order
        assertThat(notes.get(0).getCreatedAt()).isBeforeOrEqualTo(notes.get(1).getCreatedAt());
    }

    @Test
    public void givenApplicationExistsWithNoNotes_whenGetApplicationNotes_thenReturnOkWithEmptyList() throws Exception {
        // given
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, application.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        ApplicationNotesResponse response = deserialise(result, ApplicationNotesResponse.class);
        assertThat(response.getNotes()).isEmpty();
    }

    @Test
    public void givenApplicationDoesNotExist_whenGetApplicationNotes_thenReturnNotFound() throws Exception {
        // given
        UUID unknownId = UUID.randomUUID();

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, unknownId);

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNotFound(result);

        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertThat(problemDetail.getDetail()).contains("No application found with id: " + unknownId);
    }

    @Test
    public void givenUnknownRole_whenGetApplicationNotes_thenReturnForbidden() throws Exception {
        // given
        withToken(TestConstants.Tokens.UNKNOWN);

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, UUID.randomUUID());

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetApplicationNotes_thenReturnUnauthorised() throws Exception {
        // given
        withNoToken();

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_NOTES, UUID.randomUUID());

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }
}

