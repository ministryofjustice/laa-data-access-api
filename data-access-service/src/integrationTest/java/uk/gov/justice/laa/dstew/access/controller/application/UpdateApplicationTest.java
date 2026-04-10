package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoContent;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationUpdateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class UpdateApplicationTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidApplicationDataAndIncorrectHeader_whenUpdateApplication_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenValidApplicationDataAndIncorrectHeader_whenUpdateApplication_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class,
            builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            ServiceNameHeader(serviceName),
            UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  public void
      givenUpdateRequestWithNewContentAndStatus_whenUpdateApplication_thenReturnOK_andUpdateApplication()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    Map<String, Object> expectedContent =
        objectMapper.convertValue(
            DataGenerator.createDefault(ApplicationContentGenerator.class), Map.class);

    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(
            ApplicationUpdateRequestGenerator.class,
            builder ->
                builder
                    .applicationContent(expectedContent)
                    .status(ApplicationStatus.APPLICATION_SUBMITTED));

    // when
    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    ApplicationEntity actual =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertThat(expectedContent)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(actual.getApplicationContent());
    assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, actual.getStatus());
  }

  @ParameterizedTest
  @MethodSource("invalidApplicationUpdateRequestCases")
  public void givenUpdateRequestWithInvalidContent_whenUpdateApplication_thenReturnBadRequest(
      ApplicationUpdateRequest applicationUpdateRequest) throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            applicationEntity.getId());

    // then
    assertEquals(0L, applicationEntity.getVersion());
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertBadRequest(result);
  }

  @Test
  public void givenUpdateRequestWithWrongId_whenUpdateApplication_thenReturnNotFound()
      throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    // when
    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID());

    // then
    assertEquals(0L, applicationEntity.getVersion());
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"f8c3de3d-1fea-4d7c-a8b0", "not a UUID"})
  public void givenUpdateRequestWithInvalidId_whenUpdateApplication_thenReturnNotFound(String uuid)
      throws Exception {
    // given
    persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, uuid);

    // then
    assertEquals(0L, applicationEntity.getVersion());
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertBadRequest(result);
  }

  @Test
  public void givenReaderRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenUnknownRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenUpdateApplication_thenReturnUnauthorised() throws Exception {
    withNoToken();
    ApplicationUpdateRequest applicationUpdateRequest =
        DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class);

    HarnessResult result =
        patchUri(
            TestConstants.URIs.UPDATE_APPLICATION,
            applicationUpdateRequest,
            UUID.randomUUID().toString());

    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  private static Stream<Arguments> invalidApplicationUpdateRequestCases() {
    return Stream.of(
        Arguments.of(
            DataGenerator.createDefault(
                ApplicationUpdateRequestGenerator.class,
                builder -> builder.applicationContent(null))),
        null);
  }
}
