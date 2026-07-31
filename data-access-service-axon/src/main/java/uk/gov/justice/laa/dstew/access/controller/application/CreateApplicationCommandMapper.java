package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationIndividual;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Maps the generated HTTP request model to the Axon create command. */
@Component
public class CreateApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  public CreateApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a command whose aggregate identifier is extracted from the Apply content ID. */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest request, int schemaVersion) {
    return new CreateApplicationCommand(
        extractApplicationId(request.getApplicationContent()),
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

  private UUID extractApplicationId(Map<String, Object> applicationContent) {
    if (applicationContent == null || !applicationContent.containsKey("id")) {
      throw new ValidationException(
          List.of("applicationContent.id: must be present and a valid UUID"));
    }
    Object idObj = applicationContent.get("id");
    if (idObj == null) {
      throw new ValidationException(
          List.of("applicationContent.id: must be present and a valid UUID"));
    }
    String idValue = idObj.toString();
    try {
      return UUID.fromString(idValue);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          List.of("applicationContent.id: must be a valid UUID, got: " + idValue));
    }
  }

  private String schemaName(ApplicationCreateRequest request) {
    return request.getApplicationType() == ApplicationType.CCS
        ? "CssApplication.json"
        : "ApplyApplication.json";
  }

  private List<CreateApplicationIndividual> toIndividuals(
      List<IndividualCreateRequest> individuals) {
    if (individuals == null) {
      return List.of();
    }
    return individuals.stream()
        .map(
            individual ->
                new CreateApplicationIndividual(
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
