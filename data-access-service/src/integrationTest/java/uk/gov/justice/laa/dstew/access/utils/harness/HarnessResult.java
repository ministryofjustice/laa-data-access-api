package uk.gov.justice.laa.dstew.access.utils.harness;

import java.util.Map;

/**
 * Mimics the MvcResult.getResponse() surface so ResponseAsserts static helpers (assertOK,
 * assertForbidden, assertSecurityHeaders, etc.) work unchanged.
 */
public class HarnessResult {

  private final int status;
  private final Map<String, String> headers;
  private final String body;

  public HarnessResult(int status, Map<String, String> headers, String body) {
    this.status = status;
    this.headers = headers;
    this.body = body;
  }

  public Response getResponse() {
    return new Response();
  }

  public class Response {
    public int getStatus() {
      return status;
    }

    public String getHeader(String name) {
      return headers.get(name);
    }

    public String getContentAsString() {
      return body;
    }
  }
}
