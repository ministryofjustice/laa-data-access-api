package uk.gov.justice.laa.dstew.access.utils.asserters;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

public class ResponseAsserts {

  public static void assertSecurityHeaders(MvcResult response) {
    assertEquals("0", response.getResponse().getHeader("X-XSS-Protection"));
    assertEquals("DENY", response.getResponse().getHeader("X-Frame-Options"));
  }

  public static void assertContentHeaders(MvcResult response) {
    assertEquals("application/json", response.getResponse().getContentType());
  }

  public static void assertNoCacheHeaders(MvcResult response) {
    assertEquals(
        "no-cache, no-store, max-age=0, must-revalidate",
        response.getResponse().getHeader("Cache-Control"));
    assertEquals("no-cache", response.getResponse().getHeader("Pragma"));
    assertEquals("0", response.getResponse().getHeader("Expires"));
  }

  public static void assertOK(MvcResult response) {
    assertEquals(HttpStatus.OK.value(), response.getResponse().getStatus());
  }

  public static void assertNotFound(MvcResult response) {
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getResponse().getStatus());
  }

  public static void assertCreated(MvcResult response) {
    assertEquals(HttpStatus.CREATED.value(), response.getResponse().getStatus());
    assertNotNull(response.getResponse().getHeader("Location"));
  }

  public static void assertNoContent(MvcResult response) {
    assertEquals(HttpStatus.NO_CONTENT.value(), response.getResponse().getStatus());
  }

  public static void assertProblemRecord(
      HttpStatus expectedStatus,
      String expectedShortCode,
      String expectedDetail,
      MvcResult response,
      ProblemDetail actualDetail) {

    assertEquals("application/problem+json", response.getResponse().getContentType());
    assertEquals(expectedStatus.value(), response.getResponse().getStatus());
    assertEquals(expectedShortCode, actualDetail.getTitle());
    assertEquals(expectedDetail, actualDetail.getDetail());
  }

  public static void assertValidationException(
      HttpStatus expectedStatus,
      List<String> expectedValidationExceptions,
      MvcResult response,
      ValidationException validationException) {

    assertEquals("application/problem+json", response.getResponse().getContentType());
    assertEquals(expectedStatus.value(), response.getResponse().getStatus());
    assertEquals(expectedValidationExceptions, validationException.errors());
  }

  public static void assertProblemRecord(
      HttpStatus status,
      ProblemDetail expectedDetail,
      MvcResult response,
      ProblemDetail actualDetail) {
    assertProblemRecord(
        status, expectedDetail.getTitle(), expectedDetail.getDetail(), response, actualDetail);
  }

  public static void assertBadRequest(MvcResult response) {
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getResponse().getStatus());
  }

  public static void assertForbidden(MvcResult response) {
    assertEquals(HttpStatus.FORBIDDEN.value(), response.getResponse().getStatus());
  }

  public static void assertUnauthorised(MvcResult response) {
    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getResponse().getStatus());
  }
}
