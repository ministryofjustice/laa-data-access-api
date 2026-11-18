package uk.gov.justice.laa.dstew.access.bdd.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ApiResponse(int status, Map<String, String> headers, JsonNode body) {
  public boolean hasStatus(int... expectedStatuses) {
    for (int expectedStatus : expectedStatuses) {
      if (status == expectedStatus) return true;
    }
    return false;
  }
}
