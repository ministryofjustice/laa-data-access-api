package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class GetCertificateTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenIncorrectHeader_whenGetCertificate_thenReturnBadRequest(String serviceName)
      throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenNoHeader_whenGetCertificate_thenReturnBadRequest() throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_CERTIFICATE, ServiceNameHeader(serviceName), UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  public void givenExistingCertificate_whenGetCertificate_thenReturnOkWithContent()
      throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    persistedDataGenerator.createAndPersist(
        CertificateEntityGenerator.class, builder -> builder.application(application));

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_CERTIFICATE, application.getId());

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    Map actualContent = deserialise(result, Map.class);
    assertThat(actualContent).isNotNull();
    assertThat(actualContent.get("certificateNumber")).isEqualTo("TESTCERT001");
    assertThat(actualContent.get("issueDate")).isEqualTo("2026-03-03");
    assertThat(actualContent.get("validUntil")).isEqualTo("2027-03-03");
  }

  @Test
  public void givenApplicationNotExist_whenGetCertificate_thenReturnNotFound() throws Exception {
    // given
    UUID notExistApplicationId = UUID.randomUUID();

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_CERTIFICATE, notExistApplicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals(
        "No application found with id: " + notExistApplicationId, problemDetail.getDetail());
  }

  @Test
  public void givenNoCertificateExists_whenGetCertificate_thenReturnNotFound() throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_CERTIFICATE, application.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals(
        "No certificate found for application id: " + application.getId(),
        problemDetail.getDetail());
  }

  @Test
  public void givenUnknownRole_whenGetCertificate_thenReturnForbidden() throws Exception {
    // given
    withToken(TestConstants.Tokens.UNKNOWN);
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_CERTIFICATE, application.getId());

    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenGetCertificate_thenReturnUnauthorised() throws Exception {
    // given
    withNoToken();
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_CERTIFICATE, application.getId());

    // then
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }
}
