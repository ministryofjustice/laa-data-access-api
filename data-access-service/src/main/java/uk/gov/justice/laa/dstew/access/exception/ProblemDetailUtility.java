package uk.gov.justice.laa.dstew.access.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Utility class for creating custom ProblemDetail instances. */
public class ProblemDetailUtility {

  /**
   * Creates a custom ProblemDetail with given status and detail message. Sets title to status
   * reason phrase and type to null.
   *
   * @param status HTTP status.
   * @param detailMessage problem detail message.
   * @return custom ProblemDetail.
   */
  public static ProblemDetail getCustomProblemDetail(HttpStatus status, String detailMessage) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detailMessage);
    problemDetail.setTitle(status.getReasonPhrase());
    problemDetail.setType(null);
    return problemDetail;
  }
}
