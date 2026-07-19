package uk.gov.justice.laa.dstew.access.controller.application;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.assignment.CaseworkerAssignment;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Maps the generated caseworker-assignment request to command-side transport data. */
@Component
public class AssignCaseworkerRequestMapper {

  private final ObjectMapper objectMapper;

  public AssignCaseworkerRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps and serialises the assignment request. */
  public CaseworkerAssignment toAssignment(CaseworkerAssignRequest request) {
    if (request.getApplicationIds() == null || request.getApplicationIds().size() != 1) {
      throw new ValidationException(
          java.util.List.of("Exactly one application ID must be provided"));
    }
    var applicationId = request.getApplicationIds().getFirst();
    return new CaseworkerAssignment(
        request.getCaseworkerId(),
        applicationId,
        serialise(request),
        request.getEventHistory() == null ? null : request.getEventHistory().getEventDescription());
  }

  private String serialise(CaseworkerAssignRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise CaseworkerAssignRequest", exception);
    }
  }
}
