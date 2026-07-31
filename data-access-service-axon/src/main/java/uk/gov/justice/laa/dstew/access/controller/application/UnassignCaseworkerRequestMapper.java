package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerFromApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;

/** Maps and serialises generated caseworker-unassignment requests. */
@Component
public class UnassignCaseworkerRequestMapper {

  private final ObjectMapper objectMapper;

  public UnassignCaseworkerRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps an unassignment request to its aggregate command and audit data. */
  public UnassignCaseworkerFromApplicationCommand toCommand(
      UUID applicationId, CaseworkerUnassignRequest request) {
    String eventDescription =
        request.getEventHistory() == null ? null : request.getEventHistory().getEventDescription();
    return new UnassignCaseworkerFromApplicationCommand(
        applicationId, serialise(request), eventDescription, Instant.now());
  }

  private String serialise(CaseworkerUnassignRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise CaseworkerUnassignRequest", exception);
    }
  }
}
