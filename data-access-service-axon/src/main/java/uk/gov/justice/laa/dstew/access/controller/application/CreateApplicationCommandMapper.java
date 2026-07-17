package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;

/** Maps the generated HTTP request model to the Axon synchronous create command. */
@Component
public class CreateApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  public CreateApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a command whose aggregate identifier is derived from the application content. */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest request, int schemaVersion) {
    return new CreateApplicationCommand(
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        toIndividuals(request.getIndividuals()),
        serialise(request),
        schemaVersion,
        schemaName(request),
        request.getApplicationType() == null
            ? ApplicationType.APPLY.name()
            : request.getApplicationType().name());
  }

  private String schemaName(ApplicationCreateRequest request) {
    return request.getApplicationType() == ApplicationType.CCS
        ? "CssApplication.json"
        : "ApplyApplication.json";
  }

  private List<ApplicationIndividual> toIndividuals(List<IndividualCreateRequest> individuals) {
    if (individuals == null) {
      return List.of();
    }
    return individuals.stream()
        .map(
            individual ->
                new ApplicationIndividual(
                    null,
                    individual.getFirstName(),
                    individual.getLastName(),
                    individual.getDateOfBirth(),
                    individual.getDetails(),
                    individual.getType() == null ? null : individual.getType().name()))
        .toList();
  }

  private String serialise(ApplicationCreateRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ApplicationCreateRequest", exception);
    }
  }
}
