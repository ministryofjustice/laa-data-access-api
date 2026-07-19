package uk.gov.justice.laa.dstew.access.controller.application;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;

/** Maps and serialises generated caseworker-unassignment requests. */
@Component
public class UnassignCaseworkerRequestMapper {

  private final ObjectMapper objectMapper;

  public UnassignCaseworkerRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Serialises the request for storage outside the event stream. */
  public String serialise(CaseworkerUnassignRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise CaseworkerUnassignRequest", exception);
    }
  }
}
