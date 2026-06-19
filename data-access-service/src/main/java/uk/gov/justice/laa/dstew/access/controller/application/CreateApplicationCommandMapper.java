package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.IndividualCommand;

/** Maps {@link ApplicationCreateRequest} to {@link CreateApplicationCommand}. */
public class CreateApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper with the provided Jackson ObjectMapper.
   *
   * @param objectMapper the Jackson ObjectMapper to use for serialisation
   */
  public CreateApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts an {@link ApplicationCreateRequest} to a {@link CreateApplicationCommand}.
   *
   * @param req the HTTP request model
   * @return the command record
   */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest req) {
    return CreateApplicationCommand.builder()
        .status(req.getStatus() != null ? req.getStatus().name() : null)
        .laaReference(req.getLaaReference())
        .applicationContent(req.getApplicationContent())
        .individuals(toIndividualCommands(req.getIndividuals()))
        .serialisedRequest(serialise(req))
        .build();
  }

  private List<IndividualCommand> toIndividualCommands(List<IndividualCreateRequest> individuals) {
    if (individuals == null) {
      return List.of();
    }
    return individuals.stream().map(this::toIndividualCommand).toList();
  }

  private IndividualCommand toIndividualCommand(IndividualCreateRequest req) {
    return IndividualCommand.builder()
        .firstName(req.getFirstName())
        .lastName(req.getLastName())
        .dateOfBirth(req.getDateOfBirth())
        .individualContent(req.getDetails())
        .type(req.getType() != null ? req.getType().name() : null)
        .build();
  }

  @ExcludeFromGeneratedCodeCoverage
  private String serialise(ApplicationCreateRequest req) {
    try {
      return objectMapper.writeValueAsString(req);
    } catch (JacksonException e) {
      throw new DomainEventPublishException(
          "Unable to serialise ApplicationCreateRequest for domain event");
    }
  }
}
